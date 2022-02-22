package de.bjrke.factorio.optimizer.common.business

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import kotlin.math.round

class utilsTest :
    FunSpec({
        withData(
            0.00001234567 to 0.0,
            0.1234567890 to round(0.1234567890 * 1000.0) / 1000.0,
            1.5 to 1.5,
            100.12345 to 100.1,
            9876.5432 to 9877,
            123456789.1234 to 123456789.0
        ) { (from, expected) -> superRound(from).shouldBe(expected) }
    })
