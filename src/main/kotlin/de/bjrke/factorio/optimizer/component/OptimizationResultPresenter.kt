package de.bjrke.factorio.optimizer.component

import de.bjrke.factorio.optimizer.model.Material
import de.bjrke.factorio.optimizer.ChangeMaterial
import de.bjrke.factorio.optimizer.NormalizeTo
import de.bjrke.factorio.optimizer.OptimizerAppState
import de.bjrke.factorio.optimizer.model.OptimizationResult
import de.bjrke.factorio.optimizer.round2
import kotlinx.css.Color
import kotlinx.css.backgroundColor
import kotlinx.html.TD
import kotlinx.html.js.onClickFunction
import react.*
import react.dom.*
import react.redux.rConnect
import redux.RAction
import redux.WrapperAction
import styled.css
import styled.styledThead

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
                props.optimizationResult.factories.forEach { (m, factoryProduction) ->
                    tr {
                        td {
                            a {
                                attrs {
                                    onClickFunction = { props.changeMaterial(m) }
                                }
                                child(MaterialIcon::class) {
                                    attrs {
                                        material = m
                                        size = 32
                                    }
                                }
                                +m.name
                            }

                        }
                        td {
                            renderAmount(factoryProduction.amount)
                        }
                        td {
                            renderAmount(factoryProduction.production(m))
                        }
                    }
                }
            }
        }
    }

    private fun RDOMBuilder<TD>.renderAmount(amount: Double) {
        a {
            attrs {
                onClickFunction = { props.changeNormalizeTo(amount) }
            }
            +round2(amount / props.normalizeTarget).toString()
        }
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
            changeNormalizeTo = { dispatch(NormalizeTo(it)) }
        }
    )(OptimizationResultPresenter::class.rClass)
