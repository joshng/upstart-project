package upstart.dynamodb.heterogeneous;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkState;

public class SortKeyTypePrefixExtractor implements MixedDynamoTableReader.TypeIdExtractor<SortKeyTypePrefixBean> {

  public static final Pattern TYPE_PATTERN = Pattern.compile("^#(.+?)#");
  public static final String SORT_KEY_FORMAT = "#%s#%s";

  @Override
  public String extractTypeId(Map<String, AttributeValue> data) {
    String sortKey = data.get(MixedTableDynamoBean.SORT_KEY_ATTRIBUTE).s();
    Matcher matcher = TYPE_PATTERN.matcher(sortKey);
    checkState(matcher.find(), "No type discriminator found in sort key: %s", sortKey);
    return matcher.group(1);
  }
}
