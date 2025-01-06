package org.danilopianini.gradle.test

import io.github.mirkofelice.api.Testkit
import io.kotest.core.spec.style.StringSpec
import java.io.File

class Tests :
    StringSpec({

        val projectName = "publish-on-central"
        val sep = File.separator
        val baseFolder = Testkit.DEFAULT_TEST_FOLDER + "org${sep}danilopianini${sep}gradle${sep}test$sep"

        fun Testkit.projectTest(
            folder: String,
            showOutput: Boolean = false,
        ) = this.test(
            projectName,
            baseFolder + folder,
            forwardOutput = showOutput,
        )

        "Test test0" {
            Testkit.projectTest("test0", true)
        }

        "Test ktjs" {
            Testkit.projectTest("ktjs")
        }

        "Test ktmultiplatform" {
            Testkit.projectTest("ktmultiplatform")
        }

        "Test multiproject" {
            Testkit.projectTest("multiproject")
        }
    })
