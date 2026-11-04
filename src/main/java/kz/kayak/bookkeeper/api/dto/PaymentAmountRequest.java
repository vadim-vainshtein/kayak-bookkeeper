package kz.kayak.bookkeeper.api.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.LocalDate;

@Schema(description = "Изменение полученного платежа; пропущенные или null дата и комментарий сохраняют прежние значения")
public record PaymentAmountRequest(
        @Schema(description = "Новая сумма в тенге, не меньше уже распределённой суммы", example = "17500")
        @JsonDeserialize(using = WholeTengeDeserializer.class)
        @Positive long amount,
        @Schema(description = "Новая дата оплаты; пропущенное или null значение сохраняет прежнюю дату")
        LocalDate paidAt,
        @Schema(description = "Новый комментарий до 1000 символов; пропущенное или null значение сохраняет прежний, пустая строка очищает")
        @Size(max = 1000) String note
) {
    public PaymentAmountRequest(long amount) {
        this(amount, null, null);
    }

    /** Keep this endpoint from silently truncating a fractional JSON number to long. */
    public static class WholeTengeDeserializer extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Long.class, "Сумма оплаты должна быть целым числом в тенге");
            }
            return parser.getLongValue();
        }
    }
}
