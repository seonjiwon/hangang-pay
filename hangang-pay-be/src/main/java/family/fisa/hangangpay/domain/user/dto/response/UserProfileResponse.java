package family.fisa.hangangpay.domain.user.dto.response;

import family.fisa.hangangpay.domain.user.entity.User;
import java.time.LocalDate;

public record UserProfileResponse(
        Long userId,
        Long partyId,
        String username,
        String phoneNumber,
        LocalDate birthDate,
        String region) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getParty().getId(),
                user.getUsername(),
                user.getPhoneNumber(),
                user.getBirthDate(),
                user.getRegion());
    }
}
