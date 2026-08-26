import { MockWalletCheckout } from "@/components/payment/mock-wallet-checkout"

export default async function MockWalletPage({
  params,
  searchParams,
}: {
  params: Promise<{ paymentCode: string }>
  searchParams: Promise<{ bookingId?: string }>
}) {
  const [{ paymentCode }, { bookingId }] = await Promise.all([params, searchParams])
  return <MockWalletCheckout paymentCode={paymentCode} bookingPublicId={bookingId} />
}
