package base.api.feature.posscan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Kết quả một lần hỏi mã mới.
 *
 * latestId là con trỏ để lần hỏi sau gửi lên qua afterId — nhờ vậy máy bán hàng
 * không bao giờ xử lý lại một mã hai lần.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScanEventFeedResponse {

    private Long latestId;
    private List<ScanEventResponse> events;
}
