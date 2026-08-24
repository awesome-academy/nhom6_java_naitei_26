import { apiClient } from "@/lib/api/client"
import type {
  CancellationPolicy,
  CancellationPolicyCreateRequest,
  CancellationPolicyUpdateRequest,
} from "@/types/cancellation-policy"

const CANCELLATION_POLICIES_PATH = "/api/cancellation-policies"

function cancellationPolicyPath(code: string): string {
  return `${CANCELLATION_POLICIES_PATH}/${encodeURIComponent(code)}`
}

export function getCancellationPolicies(): Promise<CancellationPolicy[]> {
  return apiClient.get<CancellationPolicy[]>(CANCELLATION_POLICIES_PATH)
}

export function createCancellationPolicy(
  request: CancellationPolicyCreateRequest
): Promise<CancellationPolicy> {
  return apiClient.post<CancellationPolicy>(CANCELLATION_POLICIES_PATH, request)
}

export function updateCancellationPolicy(
  code: string,
  request: CancellationPolicyUpdateRequest
): Promise<CancellationPolicy> {
  return apiClient.put<CancellationPolicy>(cancellationPolicyPath(code), request)
}

export function deactivateCancellationPolicy(code: string): Promise<void> {
  return apiClient.delete<void>(cancellationPolicyPath(code))
}
