package de.bjrke.factorio.optimizer.component

import de.bjrke.factorio.optimizer.*
import de.bjrke.factorio.optimizer.model.Material
import de.bjrke.factorio.optimizer.model.OptimizationResult
import kotlinext.js.Object
import kotlinx.css.Color
import kotlinx.css.backgroundColor
import kotlinx.html.A
import kotlinx.html.js.onClickFunction
import react.RBuilder
import react.RProps
import react.dom.*
import react.invoke
import react.rFunction
import react.redux.rConnect
import react.router.dom.RouteResultProps
import redux.RAction
import redux.WrapperAction
import styled.css
import styled.styledThead
import kotlin.math.round

fun round2(d: Double) = round(d * 100.0) / 100.0

interface OptimizationResultPresenterStateProps : RProps {
    var optimizationResult: OptimizationResult
    var normalizeTarget: Double
}

interface OptimizationResultPresenterDispatchProps : RProps {
    var changeMaterial: (Material) -> Unit
    var changeNormalizeTo: (Double) -> Unit
}

interface OptimizationResultPresenterProps : OptimizationResultPresenterStateProps,
    OptimizationResultPresenterDispatchProps

private val optimizationResultPresenter =
    rFunction<OptimizationResultPresenterProps>("optimizationResultPresenter") { props ->

        fun amountText(amount: Double) = "x " + round2(amount * props.normalizeTarget).toString()

        fun <X : kotlinx.html.Tag> RDOMBuilder<X>.amountLink(amount: Double, block: RDOMBuilder<A>.() -> Unit) =
            a {
                attrs {
                    onClickFunction = { props.changeNormalizeTo(amount) }
                }
                block()
            }

        table {
            styledThead {
                css {
                    backgroundColor = Color.lightGray
                }
                tr {
                    th {
                        +"material"
                    }
                    th {
                        +"factories"
                    }
                    th {
                        +"production"
                    }
                }
            }

            tbody {
                props.optimizationResult.factories.forEach { (material, factoryProduction) ->
                    tr {
                        td {
                            a {
                                attrs {
                                    onClickFunction = { props.changeMaterial(material) }
                                }
                                icon(material.name)
                                +material.name
                            }

                        }
                        td {
                            amountLink(factoryProduction.amount) {
                                icon(factoryProduction.factory.identifier)
                                +amountText(factoryProduction.amount)
                            }
                        }
                        td {
                            val amount = factoryProduction.production(material)
                            amountLink(amount) {
                                icon(material.name)
                                +amountText(amount)
                            }
                        }
                    }
                }
            }
        }
    }

private val reduxOptimizationResultPresenter =
    rConnect<OptimizerAppState, RAction, WrapperAction, RProps, OptimizationResultPresenterStateProps, OptimizationResultPresenterDispatchProps, OptimizationResultPresenterProps>(
        { state, _ ->
            optimizationResult = state.optimizationResult
            normalizeTarget = state.normalizeTarget
        },
        { dispatch, _ ->
            changeMaterial = { dispatch(ChangeMaterial(it)) }
            changeNormalizeTo = { dispatch(NormalizeTo(1 / it)) }
        }
    )(optimizationResultPresenter)

fun RBuilder.reduxOptimizationResultPresenter() = reduxOptimizationResultPresenter {}

private val urlOptimizationResultPresenter =
    rConnect<OptimizerAppState, RAction, WrapperAction, RouteResultProps<UrlProps>, OptimizationResultPresenterStateProps, OptimizationResultPresenterDispatchProps, OptimizationResultPresenterProps>(
        { state, routeResultProps ->
            optimizationResult = state.optimizationResult
            normalizeTarget = routeResultProps.match.params.normalizeTarget
        },
        { dispatch, routeResultProps ->
            changeMaterial = { dispatch(ChangeMaterial(it)) }
            changeNormalizeTo = { routeResultProps.changeUrl { normalizeTarget = 1 / it } }
        }
    )(optimizationResultPresenter)

fun RBuilder.urlOptimizationResultPresenter(props: RouteResultProps<UrlProps>) {
    urlOptimizationResultPresenter { Object.assign(attrs, props) }
}
