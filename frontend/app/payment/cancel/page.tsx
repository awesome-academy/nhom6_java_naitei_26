import { PaymentResultPage } from "@/components/payment/payment-result-page"

export default function PaymentCancelPage({
  searchParams,
}: {
  searchParams: Promise<{ bookingId?: string; paymentCode?: string }>
}) {
  return <PaymentResultPage outcome="cancel" searchParams={searchParams} />
}
