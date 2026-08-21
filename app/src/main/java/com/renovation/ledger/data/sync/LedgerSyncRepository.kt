package com.renovation.ledger.data.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.room.withTransaction
import com.renovation.ledger.data.local.AppDatabase
import com.renovation.ledger.data.local.dao.BudgetItemDao
import com.renovation.ledger.data.local.dao.PaymentDao
import com.renovation.ledger.data.local.dao.ProjectDao
import com.renovation.ledger.data.local.mapper.toEntity
import com.renovation.ledger.data.prefs.TaxonomyPrefs
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.remote.ApiErrorMessages
import com.renovation.ledger.data.remote.BindPhoneRequestDto
import com.renovation.ledger.data.remote.CreateLedgerRequestDto
import com.renovation.ledger.data.remote.ImportLedgerRequestDto
import com.renovation.ledger.data.remote.JoinInviteRequestDto
import com.renovation.ledger.data.remote.LedgerApi
import com.renovation.ledger.data.remote.LedgerSnapshotDto
import com.renovation.ledger.data.remote.PutItemRequestDto
import com.renovation.ledger.data.remote.RenameLedgerRequestDto
import com.renovation.ledger.data.remote.SmsLoginRequestDto
import com.renovation.ledger.data.remote.SmsSendRequestDto
import com.renovation.ledger.data.remote.SmsSendResponseDto
import com.renovation.ledger.data.remote.UpdateMeRequestDto
import com.renovation.ledger.data.remote.WeChatLoginRequestDto
import com.renovation.ledger.data.local.entity.ProjectEntity
import com.renovation.ledger.data.repo.ProjectRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerSyncRepository @Inject constructor(
    private val api: LedgerApi,
    private val userPrefs: UserPrefs,
    private val taxonomyPrefs: TaxonomyPrefs,
    private val projectRepository: Lazy<ProjectRepository>,
    private val db: AppDatabase,
    private val projectDao: ProjectDao,
    private val itemDao: BudgetItemDao,
    private val paymentDao: PaymentDao,
    @ApplicationContext private val app: Context,
) {
    private suspend fun authHeader(): String {
        val jwt = userPrefs.jwt.first() ?: error("请重新登录")
        return "Bearer $jwt"
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: HttpException) {
            rethrowMapped(e)
        } catch (e: Exception) {
            error(ApiErrorMessages.fromThrowable(e))
        }
    }

    /** 未登录也可调用的接口：401 不当作「请重新登录」，也不清 JWT。 */
    private suspend fun <T> publicApiCall(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: HttpException) {
            when (e.code()) {
                401, 403 -> error(
                    ApiErrorMessages.fromHttp(e).takeIf { it != "请重新登录" && it != "没有权限" }
                        ?: "无法访问接口（${e.code()}）。请确认服务器已更新并重启，地址是否为电脑局域网",
                )
                else -> error(ApiErrorMessages.fromHttp(e))
            }
        } catch (e: Exception) {
            error(ApiErrorMessages.fromThrowable(e))
        }
    }

    private suspend fun rethrowMapped(e: HttpException): Nothing {
        when (e.code()) {
            401 -> {
                userPrefs.setJwt(null, null)
                error("请重新登录")
            }
            403 -> error("没有权限")
            410 -> error("邀请已失效")
            else -> error(ApiErrorMessages.fromHttp(e))
        }
    }

    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun wechatLogin(code: String, client: String = "app") {
        val res = publicApiCall { api.wechatLogin(WeChatLoginRequestDto(code = code, client = client)) }
        userPrefs.setJwt(res.token, res.userId, res.phone)
        userPrefs.setNickname(res.nickname)
    }

    suspend fun sendSmsCode(phone: String): SmsSendResponseDto =
        publicApiCall { api.smsSend(SmsSendRequestDto(phone = phone.trim())) }

    suspend fun smsLogin(phone: String, code: String) {
        val res = publicApiCall {
            api.smsLogin(SmsLoginRequestDto(phone = phone.trim(), code = code.trim()))
        }
        userPrefs.setJwt(res.token, res.userId, res.phone)
        userPrefs.setNickname(res.nickname)
    }

    suspend fun logout() {
        userPrefs.setJwt(null, null)
    }

    suspend fun fetchMe() {
        if (userPrefs.jwt.first() == null) return
        val me = apiCall { api.getMe(authHeader()) }
        userPrefs.setNickname(me.nickname)
        userPrefs.setPhone(me.phone)
    }

    suspend fun updateNickname(nickname: String): String {
        val value = nickname.trim().ifBlank { "我" }
        if (userPrefs.jwt.first() == null) {
            userPrefs.setNickname(value)
            return value
        }
        val me = apiCall { api.updateMe(authHeader(), UpdateMeRequestDto(nickname = value)) }
        userPrefs.setNickname(me.nickname)
        userPrefs.setPhone(me.phone)
        return me.nickname
    }

    suspend fun bindPhone(phoneCode: String, client: String = "app") {
        val res = apiCall {
            api.bindPhone(authHeader(), BindPhoneRequestDto(phoneCode = phoneCode, client = client))
        }
        userPrefs.setJwt(res.token, res.userId, res.phone)
        userPrefs.setNickname(res.nickname)
    }

    /** 开发面板测通：不写登录态。 */
    suspend fun pingHealth(): String {
        val res = publicApiCall { api.health() }
        if (!res.ok) error("服务异常")
        return "连通成功"
    }

    suspend fun importCurrent(): String {
        val (project, items) = projectRepository.get().snapshotCurrentProjectWithItems()
        if (!project.cloudLedgerId.isNullOrBlank()) {
            pullCurrent()
            return project.cloudLedgerId
        }
        val snapshot = apiCall {
            api.importLedger(
                authHeader(),
                ImportLedgerRequestDto(
                    localId = project.id,
                    name = project.name,
                    items = items.map { LedgerSnapshotMapper.toDto(it) },
                    taxonomy = LedgerSnapshotMapper.toTaxonomyDto(taxonomyPrefs.snapshot()),
                ),
            )
        }
        applySnapshot(project.id, snapshot)
        return snapshot.id
    }

    suspend fun createCloudForCurrent() {
        val (project, _) = projectRepository.get().snapshotCurrentProjectWithItems()
        if (!project.cloudLedgerId.isNullOrBlank()) return
        val snapshot = apiCall {
            api.createLedger(
                authHeader(),
                CreateLedgerRequestDto(name = project.name, localId = project.id),
            )
        }
        applySnapshot(project.id, snapshot)
    }

    suspend fun refreshOnOpen() {
        if (userPrefs.jwt.first() == null) return
        runCatching { fetchMe() }
        val summaries = apiCall { api.listLedgers(authHeader()) }
        val existingCloudIds = projectDao.getAll().mapNotNull { it.cloudLedgerId }.toSet()
        summaries.filter { it.id !in existingCloudIds }.forEach { summary ->
            projectDao.upsert(
                ProjectEntity(
                    id = summary.id,
                    name = summary.name,
                    memberNamesCsv = "",
                    cloudLedgerId = summary.id,
                    cloudRevision = summary.revision,
                    pendingSync = false,
                ),
            )
        }
        pullCurrent()
    }

    suspend fun pullCurrent() {
        val (project, items) = projectRepository.get().snapshotCurrentProjectWithItems()
        val cloudId = project.cloudLedgerId ?: return
        if (project.pendingSync) {
            items.forEach { item ->
                runCatching { pushItem(item.id) }
            }
        }
        val snapshot = apiCall { api.getLedger(authHeader(), cloudId) }
        applySnapshot(project.id, snapshot)
    }

    suspend fun renameLedger(localProjectId: String, name: String) {
        val entity = projectDao.getById(localProjectId) ?: return
        val cloudId = entity.cloudLedgerId ?: return
        if (userPrefs.jwt.first() == null) return
        val snapshot = apiCall {
            api.renameLedger(
                authHeader(),
                cloudId,
                RenameLedgerRequestDto(name = name.trim().ifBlank { "新账本" }),
            )
        }
        applySnapshot(localProjectId, snapshot)
    }

    suspend fun pushItem(itemId: String) {
        val (project, items) = projectRepository.get().snapshotCurrentProjectWithItems()
        val cloudId = project.cloudLedgerId ?: return
        val item = items.firstOrNull { it.id == itemId } ?: return
        val response = try {
            api.putItem(
                authHeader(),
                cloudId,
                itemId,
                PutItemRequestDto(
                    baseRevision = project.cloudRevision,
                    item = LedgerSnapshotMapper.toDto(item),
                ),
            )
        } catch (e: HttpException) {
            rethrowMapped(e)
        }
        when (response.code()) {
            401 -> {
                userPrefs.setJwt(null, null)
                toast("请重新登录")
                error("请重新登录")
            }
            403 -> {
                toast("没有这个账本的权限")
                error("没有这个账本的权限")
            }
            else -> {
                if (!response.isSuccessful) {
                    val msg = ApiErrorMessages.fromHttp(HttpException(response))
                    toast(msg)
                    error(msg)
                }
                val body = response.body() ?: return
                applySnapshot(project.id, body)
            }
        }
    }

    suspend fun deleteRemoteItem(cloudId: String, itemId: String, baseRevision: Long) {
        val response = try {
            api.deleteItem(authHeader(), cloudId, itemId, baseRevision)
        } catch (e: HttpException) {
            rethrowMapped(e)
        }
        val (project, _) = projectRepository.get().snapshotCurrentProjectWithItems()
        when (response.code()) {
            401 -> {
                userPrefs.setJwt(null, null)
                toast("请重新登录")
                error("请重新登录")
            }
            403 -> {
                toast("没有这个账本的权限")
                error("没有这个账本的权限")
            }
            else -> {
                if (!response.isSuccessful) {
                    val msg = ApiErrorMessages.fromHttp(HttpException(response))
                    toast(msg)
                    error(msg)
                }
                val body = response.body() ?: return
                applySnapshot(project.id, body)
            }
        }
    }

    suspend fun markPending() {
        val (project, _) = projectRepository.get().snapshotCurrentProjectWithItems()
        val entity = projectDao.getById(project.id) ?: return
        projectDao.upsert(entity.copy(pendingSync = true))
    }

    suspend fun createInviteCode(): String {
        val (project, _) = projectRepository.get().snapshotCurrentProjectWithItems()
        val cloudId = project.cloudLedgerId ?: error("请先上传账本")
        return apiCall { api.createInvite(authHeader(), cloudId) }.code
    }

    suspend fun joinInvite(code: String) {
        val snapshot = apiCall { api.joinInvite(authHeader(), JoinInviteRequestDto(code.trim())) }
        val local = projectDao.getAll().firstOrNull { it.cloudLedgerId == snapshot.id }
        val projectId = local?.id ?: projectRepository.get().createProject(snapshot.name).id
        applySnapshot(projectId, snapshot)
        userPrefs.setCurrentProjectId(projectId)
    }

    private suspend fun applySnapshot(localProjectId: String, snapshot: LedgerSnapshotDto) {
        val existing = projectDao.getById(localProjectId) ?: return
        db.withTransaction {
            projectDao.upsert(
                existing.copy(
                    name = snapshot.name,
                    cloudLedgerId = snapshot.id,
                    cloudRevision = snapshot.revision,
                    pendingSync = false,
                ),
            )
            itemDao.deleteByProject(localProjectId)
            snapshot.items.forEach { dto ->
                val domain = LedgerSnapshotMapper.toDomain(dto, localProjectId)
                itemDao.upsert(domain.toEntity())
                domain.payments.forEach { paymentDao.upsert(it.toEntity()) }
            }
        }
        val tax = snapshot.taxonomy
        if (tax.stages.isNotEmpty() || tax.categories.isNotEmpty() || tax.spaces.isNotEmpty()) {
            taxonomyPrefs.replaceCatalog(LedgerSnapshotMapper.toCatalog(tax))
        }
    }
}
