export const PAGE_ROUTES = {
  resumes: '/resumes',
  resumeDetail: (id: number) => `/resumes/${id}`,
  knowledgebase: '/knowledgebase',
  knowledgebaseUpload: '/knowledgebase/upload',
} as const

export const API_ROUTES = {
  resumes: '/api/resumes',
  resumeUpload: '/api/resumes/upload',
  resumeDetail: (id: number) => `/api/resumes/${id}/detail`,
  resumeAnalysis: (id: number) => `/api/resumes/${id}/analysis`,
  resumeDelete: (id: number) => `/api/resumes/${id}`,
  resumeReanalyze: (id: number) => `/api/resumes/${id}/reanalyze`,
  knowledgebaseList: '/api/knowledgebase/list',
  knowledgebaseUpload: '/api/knowledgebase/upload',
  knowledgebaseDetail: (id: number) => `/api/knowledgebase/${id}`,
  knowledgebaseDelete: (id: number) => `/api/knowledgebase/${id}`,
  knowledgebaseRevectorize: (id: number) => `/api/knowledgebase/${id}/revectorize`,
} as const
