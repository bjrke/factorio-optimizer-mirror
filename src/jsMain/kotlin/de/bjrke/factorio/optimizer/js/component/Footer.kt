package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.BuildInfo
import de.bjrke.factorio.optimizer.buildInfoLine
import react.FC
import react.Props
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.pre
import react.dom.html.ReactHTML.span
import react.useState
import web.cssom.ClassName
import web.window.WindowTarget
import web.window._blank

private const val PROJECT_URL = "https://gitlab.com/bjrke/factorio-optimizer"
private const val PIPELINE_URL = "$PROJECT_URL/-/pipelines/latest?ref=main"
private const val PIPELINE_BADGE_URL = "$PROJECT_URL/badges/main/pipeline.svg"
private const val FACTORIO_URL = "https://www.factorio.com/game/about"

/** The repository's `LICENSE`, bundled as `LICENSE.txt` by the `generateLicenseResource` task. */
@JsModule("./LICENSE.txt") @JsNonModule private external val licenseText: String

external interface FooterProps : Props {
    var buildInfo: String
    var license: String
}

internal val footer =
    FC<FooterProps>("footer") { props ->
        val (licenseVisible, setLicenseVisible) = useState(false)

        div {
            span { +"Open-Source: " }
            a {
                href = PROJECT_URL
                target = WindowTarget._blank
                rel = "noopener noreferrer"
                +"gitlab.com/bjrke/factorio-optimizer"
            }
            span { +" | " }
            span { +"License: " }
            button {
                className = ClassName("footer-license")
                onClick = { setLicenseVisible(true) }
                +"Apache 2.0"
            }
            span { +" | " }
            a {
                href = PIPELINE_URL
                target = WindowTarget._blank
                rel = "noopener noreferrer"
                img {
                    src = PIPELINE_BADGE_URL
                    alt = "pipeline status"
                }
            }
            span { +" | " }
            span { +"Icons and Recipes: © " }
            a {
                href = FACTORIO_URL
                target = WindowTarget._blank
                rel = "noopener noreferrer"
                +"Wube Software"
            }
            span { +" | " }
            span { +props.buildInfo }

            if (licenseVisible) {
                div {
                    className = ClassName("license-overlay")
                    role = AriaRole.dialog
                    onClick = { setLicenseVisible(false) }
                    div {
                        className = ClassName("license-dialog")
                        onClick = { it.stopPropagation() }
                        button {
                            className = ClassName("license-close")
                            onClick = { setLicenseVisible(false) }
                            +"Close"
                        }
                        pre { +props.license }
                    }
                }
            }
        }
    }

/** Renders [footer] with the real build info and the bundled LICENSE. */
internal val appFooter =
    FC<Props>("appFooter") {
        footer {
            buildInfo = buildInfoLine(BuildInfo.date, BuildInfo.commit, BuildInfo.branch)
            license = licenseText
        }
    }
