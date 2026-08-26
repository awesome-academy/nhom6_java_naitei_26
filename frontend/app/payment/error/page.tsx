import { PaymentResultPage } from "@/components/payment/payment-result-page"

export default function PaymentErrorPage({
  searchParams,
}: {
  searchParams: Promise<{ bookingId?: string; paymentCode?: string }>
}) {
  return <PaymentResultPage outcome="error" searchParams={searchParams} />
}
