import {
  PaymentResultView,
  type PaymentReturnOutcome,
} from "@/components/payment/payment-result-view"

export async function PaymentResultPage({
  outcome,
  searchParams,
}: {
  outcome: PaymentReturnOutcome
  searchParams: Promise<{ bookingId?: string; paymentCode?: string }>
}) {
  const { bookingId, paymentCode } = await searchParams
  return (
    <PaymentResultView
      outcome={outcome}
      bookingPublicId={bookingId}
      paymentCode={paymentCode}
    />
  )
}
