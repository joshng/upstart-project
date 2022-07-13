package upstart.javalin;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.http.HttpCode;
import io.javalin.http.HttpResponseException;

public class UnprocessableEntityResponse extends HttpResponseException {
  public UnprocessableEntityResponse(String message) {
    super(HttpCode.UNPROCESSABLE_ENTITY.getStatus(), message);
  }

  public UnprocessableEntityResponse() {
    this(HttpCode.UNPROCESSABLE_ENTITY.getMessage());
  }

  public UnprocessableEntityResponse(JsonMappingException e) {
    this(e.getMessage());
  }
}
