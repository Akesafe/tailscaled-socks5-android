package io.github.pk5ls20.tailscaled.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import io.github.pk5ls20.tailscaled.R

class LargeActionCard @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attributeSet, defStyleAttr) {

    private val iconView: ImageView
    private val textView: TextView
    private val subtextView: TextView

    var text: CharSequence?
        get() = textView.text
        set(value) {
            textView.text = value
        }

    var subtext: CharSequence?
        get() = subtextView.text
        set(value) {
            subtextView.text = value
            subtextView.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

    var icon: Drawable?
        get() = iconView.drawable
        set(value) {
            iconView.setImageDrawable(value)
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.component_large_action_label, this, true)

        iconView = findViewById(R.id.icon_view)
        textView = findViewById(R.id.text_view)
        subtextView = findViewById(R.id.subtext_view)

        // Set clickable and focusable
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        foreground = ContextCompat.getDrawable(context, outValue.resourceId)
        isFocusable = true
        isClickable = true

        // Apply attributes
        context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.LargeActionCard,
            defStyleAttr,
            0
        ).apply {
            try {
                icon = getDrawable(R.styleable.LargeActionCard_cardIcon)
                text = getString(R.styleable.LargeActionCard_cardText)
                subtext = getString(R.styleable.LargeActionCard_cardSubtext)
            } finally {
                recycle()
            }
        }

        // Set default appearance
        minimumHeight = resources.getDimensionPixelSize(R.dimen.large_action_card_min_height)
        radius = resources.getDimension(R.dimen.large_action_card_radius)
        elevation = resources.getDimension(R.dimen.large_action_card_elevation)
    }
}
