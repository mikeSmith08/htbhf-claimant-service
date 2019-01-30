package uk.gov.dhsc.htbhf.claimant.converter;

import org.junit.jupiter.api.Test;
import uk.gov.dhsc.htbhf.claimant.entity.Address;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimDTOTestDataFactory.aValidClaimDTO;

class AddressDTOToAddressConverterTest {

    private AddressDTOToAddressConverter converter = new AddressDTOToAddressConverter();

    @Test
    void shouldConvertAddressDTOToEquivalentAddressObject() {
        // Given
        var addressDTO = aValidClaimDTO().getClaimant().getCardDeliveryAddress();

        // When
        Address result = converter.convert(addressDTO);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAddressLine1()).isEqualTo(addressDTO.getAddressLine1());
        assertThat(result.getAddressLine2()).isEqualTo(addressDTO.getAddressLine2());
        assertThat(result.getTownOrCity()).isEqualTo(addressDTO.getTownOrCity());
        assertThat(result.getPostcode()).isEqualTo(addressDTO.getPostcode());
    }

    @Test
    void shouldNotConvertNullAddressDTO() {
        assertThatIllegalArgumentException().isThrownBy(() -> converter.convert(null));
    }

}
