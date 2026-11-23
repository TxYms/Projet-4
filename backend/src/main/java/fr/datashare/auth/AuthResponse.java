package fr.datashare.auth;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
