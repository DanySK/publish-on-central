import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe

class Test : StringSpec(
    {
        "a simple test" {
            true shouldNotBe false
        }
    }
)
