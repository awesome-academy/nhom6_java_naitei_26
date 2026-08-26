import { BookingPaymentPage } from "@/components/payment/booking-payment-page"

export default async function PaymentPage({
  params,
}: {
  params: Promise<{ publicId: string }>
}) {
  const { publicId } = await params

  return <BookingPaymentPage publicId={publicId} />
}
