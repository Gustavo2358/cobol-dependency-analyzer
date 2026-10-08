# Identidade IMD nas cópias distribuídas

Os arquivos da aplicação usam a identidade `imd`, incluindo documentação,
inventários, fixtures, nomes de arquivos e conteúdos de arquivos compactados.
Os scripts de benchmark localizam Java pelo `PATH`, sem diretório pessoal.

As evidências históricas nesta árvore são **cópias anonimizadas**, por solicitação
do usuário. Identificadores pessoais, caminhos de máquina e nomes de hospedagem
foram substituídos; não representam endereços externos ou comandos históricos
executáveis. Tempos, contagens, falhas, PARTIAL e conclusões foram preservados.
A anonimização não equivale a uma nova execução dos testes registrados.

Os hashes históricos continuam identificando os artefatos originais. Eles não
devem ser usados como hashes das cópias anonimizadas. O
[manifest de anonimização](../benchmark/identity-redaction.manifest.json) registra os hashes
anteriores e os das cópias modificadas, sem dados pessoais nos nomes dos arquivos.
Os originais exatos permanecem recuperáveis no commit-base registrado no
manifest; uma cópia de segurança também foi preservada fora da árvore da aplicação.

O remote configurado e o histórico Git pertencem à infraestrutura do repositório.
Não são alterados por esta substituição na árvore distribuída da aplicação.

## Validação da transformação

A varredura cobre nomes de arquivos, conteúdo, gzip descompactados, membros de
tar e seus metadados. A comparação é insensível a maiúsculas/minúsculas.
Foram renomeados 72 arquivos. Os 157 arquivos Java de produção são idênticos
aos do commit-base. Os dados executáveis e resultados esperados dos 20 fixtures
originais também são iguais; somente a referência documental de origem mudou.

O FAST foi executado novamente com Java 21: 856 testes, sem falhas, erros ou
skips. Os três launchers foram conferidos com Java encontrado no PATH e com
Java ausente, que deve falhar explicitamente. O launcher dos casos originais
passou em 20/20 entradas; o launcher ampliado passou em OR152. A execução
isolada do JAR e o `--check` do gerador direto também passaram.

Para comparar o conteúdo do JAR com o artefato qualificado, foi usado o mesmo
compilador Java 25 da base, com target Java 17. As 4.614 entradas descompactadas
são idênticas. Uma compilação intermediária com Java 21 tinha diferenças de
bytecode e metadados de compilação; ela não foi usada para afirmar igualdade
binária. Os hashes, recursos e checks novos estão no manifest.

A qualificação anterior de 30 fixtures ampliadas e 73 entradas CardDemo foi
reutilizada, não reexecutada nesta transformação: o código Java, as entradas
COBOL e o conteúdo do artefato são iguais. Permanecem seus limites, incluindo
os 47 casos PARTIAL. Não houve uma mudança semântica no analisador.
