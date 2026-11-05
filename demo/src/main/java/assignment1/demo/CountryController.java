package assignment1.demo;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CountryController {
    private final CountryService service;

    public CountryController(CountryService service) {
        this.service = service;
    }

    @GetMapping("/api/country")
    public CountryResponse country(
            @RequestParam(name = "latitude", required = false) String latitude,
            @RequestParam(name = "longitude", required = false) String longitude) {
        double lat = coordinate(latitude, "latitude", 90);
        double lon = coordinate(longitude, "longitude", 180);
        return service.lookup(lat, lon);
    }

    private double coordinate(String value, String name, int limit) {
        try {
            if (value == null || value.isBlank()) {
                throw new NumberFormatException();
            }
            BigDecimal number = new BigDecimal(value.trim());
            if (number.abs().compareTo(BigDecimal.valueOf(limit)) > 0) {
                throw new NumberFormatException();
            }
            return number.doubleValue();
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    name + " must be a number between -" + limit + " and " + limit);
        }
    }
}
