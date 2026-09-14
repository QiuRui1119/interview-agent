package com.interview.infrastructure.ai;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 清理即将插入 Prompt 模板的不可信文本，避免数据伪造模板边界或角色标记。
 */
@Component
public class PromptSanitizer {

  private static final Pattern CONTROL_CHARACTERS =
      Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]");
  private static final Pattern ROLE_MARKERS = Pattern.compile(
      "(?im)^\\s*(system|assistant|developer|user)\\s*:");

  public String sanitize(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }

    return ROLE_MARKERS.matcher(CONTROL_CHARACTERS.matcher(value)
            .replaceAll("")
            .replace("---知识库内容开始---", "[知识库边界文本已转义]")
            .replace("---知识库内容结束---", "[知识库边界文本已转义]"))
        .replaceAll("[角色标记已转义]:")
        .strip();
  }
}
