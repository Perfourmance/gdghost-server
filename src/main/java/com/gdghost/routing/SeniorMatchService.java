package com.gdghost.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.embedding.EmbeddingService;
import com.gdghost.embedding.TextBuilder;

/** 저장하지 않는다. 결과에서 질문을 남기면 그때 질문 작성(targetMemberId) */
@Service
public class SeniorMatchService {

    private final RoutingCandidateService candidates;
    private final RoutingReader reader;
    private final EmbeddingService embeddings;
    private final TextBuilder textBuilder;

    public SeniorMatchService(RoutingCandidateService candidates, RoutingReader reader, EmbeddingService embeddings,
            TextBuilder textBuilder) {
        this.candidates = candidates;
        this.reader = reader;
        this.embeddings = embeddings;
        this.textBuilder = textBuilder;
    }

    public SeniorMatchResponse find(long memberId, SeniorMatchRequest request) {
        String interest = reader.interestName(request.interestTagId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                        List.of(new ErrorField("interestTagId", "관심 분야를 골라 주세요."))));
        Optional<float[]> vector = embeddings.embedForSearch(
                textBuilder.searchText(request.category(), interest, request.text()), memberId);
        List<CandidateScorer.Scored> ranked = candidates.rankForSearch(memberId, request.category(),
                vector.orElse(null), request.interestTagId(), interest);
        Map<Long, RoutingReader.SeniorCard> cards = reader.seniorCards(
                ranked.stream().map(CandidateScorer.Scored::memberId).toList());
        List<SeniorMatchResponse.Item> items = new ArrayList<>();
        for (CandidateScorer.Scored scored : ranked) {
            RoutingReader.SeniorCard card = cards.get(scored.memberId());
            items.add(new SeniorMatchResponse.Item(items.size() + 1,
                    new SeniorMatchResponse.Member(card.id(), card.nickname(), card.generation(), card.status(),
                            card.position(), card.statusNote(), card.acceptCoffee()),
                    scored.reasons()));
        }
        return new SeniorMatchResponse(items, vector.isEmpty());
    }

}
