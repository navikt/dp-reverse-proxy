# dp-reverse-proxy

For kommunikasjon fra SBS til FSS.

- TODO: Bytte ut hele tjenesten med nginx reverse proxy..?

## Unngår å..
- ..måtte registrere & Deploye den "tilbydende" tjenesten i FSS i Fasit
- ..måtte sette opp den "tilbydende" tjenesten i API Gateway

## Må istedenfor..
- ..oppdatere nais.yaml med en environment-variabel når en ny tjeneste skal tilgjengeliggjøres

## Må fortsatt..
- ..registrere den "kallende" tjenesten i SBS i Fasit (Ingen deploy)
- ..registrere den "kallende" tjenesten i SBS i API Gateway for å få en API Key (Se nedenfor)

## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

Interne henvendelser kan sendes via Slack i kanalen #team-dagpenger-dev.
For å få en API Key som gjør det mulig å kalle denne tjenesten se https://confluence.adeo.no/x/SQ6tEg
