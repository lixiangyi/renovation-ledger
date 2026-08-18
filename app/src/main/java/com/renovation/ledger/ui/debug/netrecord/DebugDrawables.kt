package com.renovation.ledger.ui.debug.netrecord

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.core.content.ContextCompat
import com.renovation.ledger.R

internal object DebugDrawables {
    fun strokeRound(context: Context, @ColorRes strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = context.resources.getDimension(R.dimen.dimen_6)
            setStroke(
                context.resources.getDimensionPixelSize(R.dimen.dimen_2),
                ContextCompat.getColor(context, strokeColor),
            )
        }
    }

    fun solidRound(
        context: Context,
        @ColorRes color: Int,
        @DimenRes radius: Int = R.dimen.dimen_8,
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, color))
            cornerRadius = context.resources.getDimension(radius)
        }
    }
}
