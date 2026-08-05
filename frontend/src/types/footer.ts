export type PolicyDocumentId = 'terms' | 'privacy' | 'community' | 'support'

export type PolicySection = {
  heading: string
  paragraphs?: string[]
  bullets?: string[]
}

export type PolicyDocument = {
  id: PolicyDocumentId
  label: string
  title: string
  sections: PolicySection[]
}
