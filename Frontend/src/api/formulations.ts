import { request } from './client';
import type { AuthHeaders } from './client';
import type { FormulationRequest, FormulationResponse } from './types';

export function classifyFormulation(payload: FormulationRequest, auth?: AuthHeaders) {
  return request<FormulationResponse>('/api/v1/formulations/classify', {
    method: 'POST',
    body: JSON.stringify(payload),
  }, auth);
}
