package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.controller.response.SentencePlanResponse
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.IdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service.SentencePlanService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
@RequestMapping("/sentence-plan")
@PreAuthorize("hasRole('ROLE_ASSESSMENT_VIEW')")
class SentencePlanController(
  private val sentencePlanService: SentencePlanService,
) {

  @GetMapping("/{identifierType}/{identifierValue}")
  @Operation(description = "Returns all sentence plans matching the given identifier")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Sentence plans found"),
      ApiResponse(
        responseCode = "404",
        description = "No sentence plans found for the given identifier",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid identifier type",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorised",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden - requires ROLE_ASSESSMENT_VIEW",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Unexpected error",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getSentencePlans(
    @PathVariable identifierType: IdentifierType,
    @PathVariable identifierValue: String,
  ): List<SentencePlanResponse> = sentencePlanService.getSentencePlans(identifierType, identifierValue)
}