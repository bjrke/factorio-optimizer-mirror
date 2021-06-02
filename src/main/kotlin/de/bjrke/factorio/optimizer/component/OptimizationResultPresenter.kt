package de.bjrke.factorio.optimizer.component

import de.bjrke.factorio.optimizer.ChangeMaterial
import de.bjrke.factorio.optimizer.NormalizeTo
import de.bjrke.factorio.optimizer.OptimizerAppState
import de.bjrke.factorio.optimizer.model.Material
import de.bjrke.factorio.optimizer.model.OptimizationResult
import kotlinx.css.Color
import kotlinx.css.backgroundColor
import kotlinx.html.A
import kotlinx.html.js.onClickFunction
import react.*
import react.dom.*
import react.redux.rConnect
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


class OptimizationResultPresenter(props: OptimizationResultPresenterProps) :
    RComponent<OptimizationResultPresenterProps, RState>(props) {

    override fun RBuilder.render() {
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
                                icon {
                                    name = material.name
                                }
                                +material.name
                            }

                        }
                        td {
                            amountLink(factoryProduction.amount) {
                                icon {
                                    name = factoryProduction.factory.identifier
                                }
                                +amountText(factoryProduction.amount)
                            }
                        }
                        td {
                            val amount = factoryProduction.production(material)
                            amountLink(amount) {
                                icon {
                                    name = material.name
                                }
                                +amountText(amount)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun amountText(amount: Double) = "x " + round2(amount * props.normalizeTarget).toString()

    private fun <X : kotlinx.html.Tag> RDOMBuilder<X>.amountLink(amount: Double, block: RDOMBuilder<A>.() -> Unit) =
        a {
            attrs {
                onClickFunction = { props.changeNormalizeTo(amount) }
            }
            block()
        }
}

val optimizationResultPresenter: RClass<RProps> =
    rConnect<OptimizerAppState, RAction, WrapperAction, RProps, OptimizationResultPresenterStateProps, OptimizationResultPresenterDispatchProps, OptimizationResultPresenterProps>(
        { state, _ ->
            optimizationResult = state.optimizationResult
            normalizeTarget = state.normalizeTarget
        },
        { dispatch, _ ->
            changeMaterial = { dispatch(ChangeMaterial(it)) }
            changeNormalizeTo = { dispatch(NormalizeTo(1 / it)) }
        }
    )(OptimizationResultPresenter::class.rClass)
