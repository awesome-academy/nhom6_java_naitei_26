import { ProfileLayout } from "@/components/layout/profile-layout"

export default function ProfileLayoutWrapper({
  children,
}: {
  children: React.ReactNode
}) {
  return <ProfileLayout>{children}</ProfileLayout>
}
