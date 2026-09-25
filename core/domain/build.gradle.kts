plugins {
    id("at.kmp.library")
    id("at.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(projects.core.srs)
        }
    }
}
