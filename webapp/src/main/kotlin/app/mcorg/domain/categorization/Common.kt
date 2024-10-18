package app.mcorg.domain.categorization

import app.mcorg.domain.categorization.value.*

@CategoryMarker
class Common : FilterContainer()

fun Common.authors(init: Authors.() -> Unit = {}) {
    val value = Authors()
    value.init()
    filter(value)
}

fun Common.credits(init: Credits.() -> Unit = {}) {
    val value = Credits()
    value.init()
    filter(value)
}

fun Common.versions(init: BoundedVersion.() -> Unit = {}) {
    val value = BoundedVersion()
    value.init()
    filter(value)
}

fun Common.testResults(init: TestResult.() -> Unit = {}) {
    val value = TestResult()
    value.init()
    filter(value)
}
