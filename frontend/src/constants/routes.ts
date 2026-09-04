export const routes = {
  resumes: '/api/resumes',
  resumeUpload: '/api/resumes/upload',
  resumeDetail: (resumeId: number) => `/api/resumes/${resumeId}/detail`,
  resumeAnalysis: (resumeId: number) => `/api/resumes/${resumeId}/analysis`,
  resumeDelete: (resumeId: number) => `/api/resumes/${resumeId}`,
  resumeReanalyze: (resumeId: number) => `/api/resumes/${resumeId}/reanalyze`,
} as const
