import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
open class OasysVersionedEntityResponse(
  open var sentencePlanId: UUID = UUID(0, 0),
)
