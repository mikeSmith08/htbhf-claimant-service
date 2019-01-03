package uk.gov.dhsc.htbhf.claimant.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.ResponseEntity
import spock.lang.Specification
import uk.gov.dhsc.htbhf.claimant.model.ClaimDTO
import uk.gov.dhsc.htbhf.claimant.service.ClaimService

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import static org.springframework.http.HttpStatus.BAD_REQUEST
import static org.springframework.http.HttpStatus.CREATED
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimDTOTestDataFactory.*

@SpringBootTest(webEnvironment = RANDOM_PORT)
class NewClaimSpec extends Specification {

    @LocalServerPort
    int port

    @Autowired
    TestRestTemplate restTemplate

    @MockBean
    ClaimService claimService

    URI endpointUrl = URI.create("/claim")

    def "A new valid claim is accepted"() {
        given: "A valid claim request"
        def claim = aValidClaimDTO()

        when: "The request is received"
        ResponseEntity<Void> response = restTemplate.postForEntity(endpointUrl, claim, Void.class)

        then: "A created response is returned"
        assertThat(response.statusCode).isEqualTo(CREATED)
    }

    def "An invalid claim returns an error response"(ClaimDTO claim, String expectedErrorMessage, String expectedField) {
        expect:
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(endpointUrl, claim, ErrorResponse.class)
        assertErrorResponse(response, expectedField, expectedErrorMessage)

        where:
        claim                            | expectedErrorMessage               | expectedField
        aClaimDTOWithSecondNameTooLong() | "length must be between 1 and 500" | "claimant.secondName"
        aClaimDTOWithNoSecondName()      | "must not be null"                 | "claimant.secondName"
        aClaimDTOWithEmptySecondName()   | "length must be between 1 and 500" | "claimant.secondName"
        aClaimDTOWithFirstNameTooLong()  | "length must be between 0 and 500" | "claimant.firstName"
    }



    private void assertErrorResponse(ResponseEntity<ErrorResponse> response, String expectedField, String expectedErrorMessage) {
        assertThat(response.statusCode).isEqualTo(BAD_REQUEST)
        assertThat(response.body.fieldErrors.size()).isEqualTo(1)
        assertThat(response.body.fieldErrors[0].field).isEqualTo(expectedField)
        assertThat(response.body.fieldErrors[0].message).isEqualTo(expectedErrorMessage)
        assertThat(response.body.requestId).isNotNull()
        assertThat(response.body.timestamp).isNotNull()
        assertThat(response.body.status).isEqualTo(BAD_REQUEST.value())
        assertThat(response.body.message).isEqualTo("There were validation issues with the request.")
    }
}