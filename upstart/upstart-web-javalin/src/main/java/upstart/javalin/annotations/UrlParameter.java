package upstart.javalin.annotations;

/**
 * Implement this to inform {@link UrlBuilder} of the URL-parameter representation for an object
 * @see UrlBuilder
 */
public interface UrlParameter {
  String urlValue();
}
