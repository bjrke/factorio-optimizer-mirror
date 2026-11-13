package de.bjrke.factorio.optimizer.js.component

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import web.cssom.AlignItems
import web.cssom.Border
import web.cssom.Color
import web.cssom.Display
import web.cssom.FlexWrap
import web.cssom.LineStyle
import web.cssom.px
import web.cssom.rem
import web.cssom.rgb

/**
 * One row of selection controls that never breaks internally, boxed like the optimizer's result
 * groups.
 *
 * The tightest unit that may move to the next line is a logical group — the assembler radios, the
 * surface radios, the belt controls — so a group's icons stay on one line and only the header, the
 * single wrapping container around all groups, may wrap. Grouping only adds a `div`; the native
 * inputs and their `groupName`/label association stay as they are, so keyboard and screen-reader
 * behaviour does not change.
 */
internal fun ChildrenBuilder.selectionGroup(content: ChildrenBuilder.() -> Unit) {
    div {
        css {
            display = Display.flex
            flexWrap = FlexWrap.nowrap
            alignItems = AlignItems.center
            border = Border(1.px, LineStyle.solid, rgb(120, 120, 120))
            // the theme token wins; the literal is the light fallback for contexts without the
            // stylesheet tokens (e.g. component tests)
            borderColor = Color("var(--border, #787878)")
            borderRadius = 0.25.rem
            padding = 0.25.rem
            marginRight = 0.25.rem
            marginBottom = 0.25.rem
        }
        content()
    }
}

/**
 * The inner box of a control that is alone in its group. It gives that control the same margin and
 * padding as an `.icon-option`, so a single control's group is the same size as a group of
 * selectable icons instead of hugging a shorter control.
 */
internal fun ChildrenBuilder.controlBox(content: ChildrenBuilder.() -> Unit) {
    div {
        css {
            display = Display.inlineFlex
            alignItems = AlignItems.center
            padding = 2.px
            margin = 0.px
        }
        content()
    }
}
