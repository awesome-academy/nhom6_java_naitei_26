import { PaymentResultPage } from "@/components/payment/payment-result-page"

export default function PaymentSuccessPage({
  searchParams,
}: {
  searchParams: Promise<{ bookingId?: string; paymentCode?: string }>
}) {
  return <PaymentResultPage outcome="success" searchParams={searchParams} />
}
