package com.interview.modules.knowledgebase.service;

import com.interview.modules.knowledgebase.model.RetrievedChunkDTO;
import com.interview.modules.knowledgebase.model.RetrievalRequest;
import java.util.List;

public interface KnowledgeRetrievalService {

  List<RetrievedChunkDTO> retrieve(RetrievalRequest request);
}
