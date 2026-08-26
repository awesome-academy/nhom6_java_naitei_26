import { CustomerInvoiceView } from "@/components/invoice/customer-invoice-view"

export default async function CustomerInvoicePage({
  params,
}: {
  params: Promise<{ publicId: string }>
}) {
  const { publicId } = await params

  return <CustomerInvoiceView bookingPublicId={publicId} />
}
