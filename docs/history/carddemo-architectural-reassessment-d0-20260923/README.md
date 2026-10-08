# CardDemo — discovery D0 histórico

Investigação de 2026-09-23, preservada como evidência e proposta. Seus resultados,
pins e estados de revisão descrevem aquela execução; não representam o estado
atual do produto nem autorizam implementação. O baseline histórico observado era
**4/73 reach dependency**, todos `PARTIAL`, e a recomendação era a arquitetura C.

## Leitura

- [Resumo executivo](D0_EXECUTIVE_SUMMARY.md), [recomendação](D0_RECOMMENDATION.md)
  e [índice de evidências](D0_EVIDENCE_INDEX.md).
- [Deep dive F2](D0_F2_DEEP_DIVE.md) e [matriz de 1.964 relações](D0_F2_CLUSTER_MATRIX.csv).
- [Scorecard A/B/C](D0_ARCHITECTURE_SCORECARD.md) e [roadmap proposto](D0_ROADMAP_PROPOSAL.md).
- [Resumo dos 73 programas](canonical-summary/report.md),
  [classificação](canonical-summary/classification.csv),
  [agregado](canonical-summary/summary.json) e [comandos históricos](canonical-summary/commands.md).

Os 20 relatórios, a matriz e os resumos permanecem nesta árvore. Links para dados
brutos, probes e cópias de referência apontam para o commit histórico imutável.
Apenas os destinos desses links foram atualizados; resultados e conclusões foram
preservados.

## Evidência integral no histórico

A [publicação original completa](https://github.com/imd/proleap-poc/tree/5a2eaa333fc74da8d1229f7203716e4e775ed060/docs/history/carddemo-architectural-reassessment-d0-20260923) está no commit
`5a2eaa333fc74da8d1229f7203716e4e775ed060`. Ela contém os três bundles, probes, ferramentas,
snapshots de referência, inventário, manifestos e verificador, incluindo todos os
**5.911 arquivos arquivados**. Os hashes dos bundles continuam disponíveis em
[ARCHIVES.sha256](ARCHIVES.sha256), e o resultado da auditoria original está em
[PUBLICATION_VALIDATION.json](PUBLICATION_VALIDATION.json).

Esses dados brutos foram retirados da árvore atual para reduzir o snapshot de
fontes. Eles não são entradas da aplicação, do build ou dos testes atuais. A
remoção não reescreve o histórico: os bytes originais continuam recuperáveis.
Os manifestos e o resultado da auditoria descrevem a publicação original, não
uma nova execução sobre o snapshot reduzido.

Para recuperar e verificar a publicação integral, execute a partir da raiz de um
checkout Git que contenha o commit indicado. Escolha uma pasta de destino vazia:

```bash
mkdir /tmp/proleap-d0-audit
git archive 5a2eaa333fc74da8d1229f7203716e4e775ed060 docs/history/carddemo-architectural-reassessment-d0-20260923 | tar -x -C /tmp/proleap-d0-audit
cd /tmp/proleap-d0-audit/docs/history/carddemo-architectural-reassessment-d0-20260923
python3 -B verify-publication.py
sha256sum -c ARCHIVES.sha256
```

Em um download sem `.git`, use o snapshot do commit acima ou os arquivos pelos
links da publicação original. A auditoria histórica exige os dados recuperados;
ela não é um gate do produto. O manual IBM e sua extração textual continuam sendo
referências externas, discriminadas pelo inventário original.

Um clone com histórico completo ainda contém os blobs antigos. A redução é da
árvore atual e dos snapshots de fonte, não do histórico Git.
