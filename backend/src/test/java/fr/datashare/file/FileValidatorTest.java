package fr.datashare.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.datashare.common.ApiException;
import fr.datashare.config.DatashareProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

class FileValidatorTest {

    private final FileValidator validator = new FileValidator(new DatashareProperties(
            "http://localhost:4200", null, null, null,
            new DatashareProperties.Files(7, 7, List.of("exe", ".BAT", " sh ")),
            new DatashareProperties.Purge("-", Duration.ofDays(30))));

    private static MockMultipartFile file(String name, int size) {
        return new MockMultipartFile("file", name, "application/octet-stream", new byte[size]);
    }

    @Test
    void accepts_a_valid_upload() {
        assertThatCode(() -> validator.validate(file("photo.jpg", 10), "secret1", 3)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("photo.jpg", 10), null, 7)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("photo.jpg", 10), "", 1)).doesNotThrowAnyException();
    }

    @Test
    void rejects_empty_file() {
        assertThatThrownBy(() -> validator.validate(file("vide.txt", 0), null, 7))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FILE_REQUIRED");
        assertThatThrownBy(() -> validator.validate(null, null, 7))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FILE_REQUIRED");
    }

    @ParameterizedTest
    @CsvSource({"setup.exe", "SCRIPT.BAT", "deploy.sh", "archive.tar.EXE"})
    void rejects_forbidden_extensions_whatever_the_case(String name) {
        assertThatThrownBy(() -> validator.validate(file(name, 1), null, 7))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FORBIDDEN_FILE_TYPE");
    }

    @Test
    void rejects_password_shorter_than_six_characters() {
        assertThatThrownBy(() -> validator.validate(file("a.txt", 1), "12345", 7))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("PASSWORD_TOO_SHORT");
    }

    @ParameterizedTest
    @CsvSource({"0", "8", "-1"})
    void rejects_expiration_outside_one_to_seven_days(int days) {
        assertThatThrownBy(() -> validator.validate(file("a.txt", 1), null, days))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_EXPIRATION");
    }

    @Test
    void sanitize_name_strips_paths_and_control_characters() {
        assertThat(FileValidator.sanitizeName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FileValidator.sanitizeName("C:\\Users\\moi\\rapport.pdf")).isEqualTo("rapport.pdf");
        assertThat(FileValidator.sanitizeName("ma\"photo\n.jpg")).isEqualTo("maphoto.jpg");
        assertThat(FileValidator.sanitizeName("  ")).isEqualTo("fichier");
        assertThat(FileValidator.sanitizeName(null)).isEqualTo("fichier");
        assertThat(FileValidator.sanitizeName("a".repeat(300) + ".txt")).hasSize(255).endsWith(".txt");
    }
}
