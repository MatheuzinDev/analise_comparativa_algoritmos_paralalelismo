# Análise Comparativa de Algoritmos com Uso de Paralelismo

Relatório da atividade prática da disciplina de Computação Concorrente e Paralela.

## Resumo

Este projeto compara três abordagens para a contagem de ocorrências de uma palavra em arquivos de texto: execução serial na CPU, execução paralela na CPU com threads e execução paralela na GPU com OpenCL. Os testes foram executados com três textos de tamanhos diferentes, três repetições por configuração e variação da quantidade de threads no método paralelo em CPU.

Os resultados foram registrados em arquivo CSV e apresentados em gráficos comparativos para analisar o tempo médio, o impacto do tamanho da entrada, o speedup da CPU paralela e o custo de uso da GPU.

## Introdução

A busca por uma palavra em um texto é uma operação simples, mas adequada para observar diferenças entre execução sequencial e paralela. Em entradas maiores, dividir o trabalho entre vários núcleos de CPU pode reduzir o tempo total de processamento. Já a GPU oferece grande quantidade de unidades de processamento, mas também possui custos de inicialização, transferência de memória e compilação do kernel OpenCL.

O trabalho implementa os três métodos solicitados no enunciado:

| Método | Descrição |
|---|---|
| `SerialCPU` | Percorre o texto normalizado sequencialmente em uma única thread. |
| `ParallelCPU` | Divide o texto em partes e processa as partes em paralelo usando `ExecutorService`. |
| `ParallelGPU` | Envia o texto e a palavra para a GPU e executa um kernel OpenCL com JOCL. |

Todos os métodos usam a mesma etapa de normalização para garantir que a comparação seja feita com a mesma regra de contagem.

## Objetivos

- Implementar um contador de palavras serial em Java.
- Implementar um contador de palavras paralelo em CPU com pool de threads.
- Implementar um contador de palavras paralelo em GPU com OpenCL.
- Executar testes com textos de tamanhos diferentes.
- Realizar pelo menos três repetições por abordagem.
- Variar a quantidade de threads no método `ParallelCPU`.
- Registrar os resultados em CSV.
- Gerar gráficos comparativos.
- Produzir um relatório em formato Markdown para o GitHub.

## Metodologia

### Entradas

Foram usados os textos disponíveis na pasta `Amostras/`:

| Arquivo | Palavra buscada | Ocorrências encontradas |
|---|---:|---:|
| `DonQuixote-388208.txt` | `que` | 21618 |
| `Dracula-165307.txt` | `the` | 8104 |
| `MobyDick-217452.txt` | `the` | 14727 |

### Normalização

Antes da contagem, os textos e a palavra buscada passam pela classe `TextNormalizer`, que aplica as seguintes regras:

- Conversão para letras minúsculas.
- Remoção de acentos.
- Substituição de pontuação e caracteres especiais por espaços.
- Padronização de espaços.
- Validação para garantir que a busca seja feita por apenas uma palavra.

Com isso, as três implementações contam a mesma palavra normalizada e evitam divergências causadas por pontuação, maiúsculas ou acentos.

### Execuções

Para cada arquivo, foram executadas as seguintes configurações:

| Abordagem | Configuração |
|---|---|
| `SerialCPU` | 1 thread |
| `ParallelCPU` | 2 threads |
| `ParallelCPU` | 4 threads |
| `ParallelCPU` | 8 threads |
| `ParallelGPU` | GPU via OpenCL |

Cada configuração foi executada três vezes. O tempo foi medido em milissegundos com `System.nanoTime()` e registrado em `resultados/resultados.csv`.

## Implementação

A estrutura principal do projeto é:

```text
|-- Amostras/
|-- opencl/
|   `-- word_count.cl
|-- resultados/
|   |-- resultados.csv
|   `-- graficos/
|-- src/
|   |-- Main.java
|   |-- BenchmarkRunner.java
|   |-- BenchmarkResult.java
|   |-- ChartGenerator.java
|   |-- CsvWriter.java
|   |-- ParallelCPUCounter.java
|   |-- ParallelGPUCounter.java
|   |-- SerialCPUCounter.java
|   |-- TextNormalizer.java
|   `-- WordCounter.java
`-- jocl-2.0.4.jar
```

### SerialCPU

O método `SerialCPU` percorre o texto normalizado palavra por palavra. Para cada palavra encontrada no texto, compara o tamanho e os caracteres com a palavra buscada. Como a execução é sequencial, o custo cresce de forma direta com o tamanho do texto.

Arquivo principal: [`src/SerialCPUCounter.java`](src/SerialCPUCounter.java)

### ParallelCPU

O método `ParallelCPU` usa `ExecutorService` para criar um pool de threads. O texto é dividido em faixas, cada thread conta as ocorrências dentro da sua parte e, ao final, os resultados parciais são somados.

Um cuidado importante está no tratamento das bordas das faixas. Se uma faixa começa no meio de uma palavra, a implementação avança até o próximo espaço para evitar contagem duplicada ou parcial.

Arquivo principal: [`src/ParallelCPUCounter.java`](src/ParallelCPUCounter.java)

### ParallelGPU

O método `ParallelGPU` usa JOCL para acessar OpenCL. O texto normalizado, a palavra buscada e o vetor de resultados são enviados para a GPU. O kernel `count_words` atribui uma posição do texto para cada work-item e marca `1` quando encontra uma ocorrência válida da palavra.

Ao final, o programa lê o vetor de resultados da GPU e soma as ocorrências na CPU.

Arquivos principais:

- [`src/ParallelGPUCounter.java`](src/ParallelGPUCounter.java)
- [`opencl/word_count.cl`](opencl/word_count.cl)

## Ambiente e Dependências

O projeto foi desenvolvido em Java e usa JOCL para executar o método em GPU.

Dependências principais:

| Dependência | Finalidade |
|---|---|
| JDK 21 ou compatível | Compilar e executar o projeto Java. |
| `jocl-2.0.4.jar` | Biblioteca Java para acesso ao OpenCL. |
| Driver OpenCL | Necessário para executar `ParallelGPU`. |
| GPU compatível com OpenCL | Dispositivo usado pelo método `ParallelGPU`. |

O arquivo `jocl-2.0.4.jar` já está na raiz do projeto. Para a correção, ele deve permanecer nesse local, pois os comandos de compilação e execução usam esse caminho.

Se o método `ParallelGPU` falhar, verifique se o driver OpenCL está instalado e se a biblioteca nativa `libOpenCL.so` está disponível no sistema.

## Como Compilar e Executar

### Compilar

```bash
javac -cp jocl-2.0.4.jar -d build src/*.java
```

### Executar benchmark completo

```bash
java -cp build:jocl-2.0.4.jar Main benchmark
```

Esse comando executa os testes padrão, gera o CSV em `resultados/resultados.csv` e atualiza os gráficos em `resultados/graficos/`.

### Executar contagem manual

```bash
java -cp build:jocl-2.0.4.jar Main contar Amostras/Dracula-165307.txt the
```

Também é possível informar a quantidade de threads do `ParallelCPU`:

```bash
java -cp build:jocl-2.0.4.jar Main contar Amostras/Dracula-165307.txt vampire 4
```

## Resultados

O arquivo completo de resultados está em [`resultados/resultados.csv`](resultados/resultados.csv). A tabela abaixo apresenta as médias das três repetições de cada configuração.

| Arquivo | Palavra | Método | Threads | Média (ms) | Ocorrências |
|---|---|---|---:|---:|---:|
| `DonQuixote-388208.txt` | `que` | `SerialCPU` | 1 | 6.166 | 21618 |
| `DonQuixote-388208.txt` | `que` | `ParallelCPU` | 2 | 9.030 | 21618 |
| `DonQuixote-388208.txt` | `que` | `ParallelCPU` | 4 | 2.290 | 21618 |
| `DonQuixote-388208.txt` | `que` | `ParallelCPU` | 8 | 1.531 | 21618 |
| `DonQuixote-388208.txt` | `que` | `ParallelGPU` | 0 | 83.136 | 21618 |
| `Dracula-165307.txt` | `the` | `SerialCPU` | 1 | 1.966 | 8104 |
| `Dracula-165307.txt` | `the` | `ParallelCPU` | 2 | 1.315 | 8104 |
| `Dracula-165307.txt` | `the` | `ParallelCPU` | 4 | 0.903 | 8104 |
| `Dracula-165307.txt` | `the` | `ParallelCPU` | 8 | 1.208 | 8104 |
| `Dracula-165307.txt` | `the` | `ParallelGPU` | 0 | 53.973 | 8104 |
| `MobyDick-217452.txt` | `the` | `SerialCPU` | 1 | 2.269 | 14727 |
| `MobyDick-217452.txt` | `the` | `ParallelCPU` | 2 | 1.671 | 14727 |
| `MobyDick-217452.txt` | `the` | `ParallelCPU` | 4 | 1.179 | 14727 |
| `MobyDick-217452.txt` | `the` | `ParallelCPU` | 8 | 1.049 | 14727 |
| `MobyDick-217452.txt` | `the` | `ParallelGPU` | 0 | 51.426 | 14727 |

### Médias gerais por método

| Método | Média geral (ms) |
|---|---:|
| `SerialCPU` | 3.467 |
| `ParallelCPU 2 threads` | 4.005 |
| `ParallelCPU 4 threads` | 1.457 |
| `ParallelCPU 8 threads` | 1.263 |
| `ParallelGPU` | 62.845 |

### Speedup médio do ParallelCPU

O speedup foi calculado pela razão entre o tempo médio do `SerialCPU` e o tempo médio do `ParallelCPU` para cada arquivo. Em seguida, foi calculada a média dos speedups dos três arquivos.

| Configuração | Speedup médio |
|---|---:|
| `ParallelCPU 2 threads` | 1.178x |
| `ParallelCPU 4 threads` | 2.264x |
| `ParallelCPU 8 threads` | 2.605x |

## Gráficos Comparativos

### Tempo médio por método

![Tempo médio por método](resultados/graficos/tempo_por_metodo.svg)

### Tempo médio por arquivo

![Tempo médio por arquivo](resultados/graficos/tempo_por_arquivo.svg)

### Speedup CPU

![Speedup CPU](resultados/graficos/speedup_cpu.svg)

## Discussão dos Resultados

Os resultados mostram que o `ParallelCPU` apresentou o melhor desempenho geral quando configurado com 4 ou 8 threads. Em `DonQuixote-388208.txt`, o tempo médio caiu de 6.166 ms no método serial para 1.531 ms com 8 threads, indicando ganho relevante com a divisão do trabalho entre núcleos de CPU.

Com 2 threads, o ganho não foi uniforme. No arquivo `DonQuixote-388208.txt`, o `ParallelCPU` com 2 threads ficou mais lento que o método serial. Isso pode ocorrer por causa do custo de criação e gerenciamento das threads, além da divisão do texto em partes. Para entradas pequenas ou configurações pouco favoráveis, o overhead do paralelismo pode superar o ganho obtido.

O `ParallelCPU` com 4 threads apresentou bom equilíbrio, sendo mais rápido que o serial nos três arquivos. Com 8 threads, o desempenho também foi bom, mas, em `Dracula-165307.txt`, o tempo médio ficou maior que com 4 threads. Isso indica que aumentar a quantidade de threads nem sempre melhora o resultado, pois há custos de coordenação e disputa por recursos da CPU.

O método `ParallelGPU` retornou as mesmas contagens dos métodos de CPU, mas apresentou tempos maiores nas amostras testadas. A principal explicação é que o custo de inicializar OpenCL, compilar o kernel, criar buffers, transferir dados para a GPU e ler o resultado de volta foi maior que o ganho obtido na contagem. Para volumes de dados muito maiores ou para kernels mais complexos, a GPU poderia se tornar mais competitiva.

Também foi observado que todas as abordagens retornaram exatamente a mesma quantidade de ocorrências em cada arquivo. Isso confirma que a normalização e a regra de contagem foram aplicadas de maneira consistente entre CPU serial, CPU paralela e GPU.

## Conclusão

O trabalho demonstra que o paralelismo em CPU pode reduzir o tempo de execução da contagem de palavras, especialmente quando são usadas 4 ou 8 threads. No conjunto de testes realizado, o `ParallelCPU` foi a abordagem mais eficiente.

A GPU funcionou corretamente e produziu as mesmas contagens, mas não foi a alternativa mais rápida para essas entradas. O resultado evidencia que paralelizar uma tarefa não garante automaticamente melhor desempenho: é necessário considerar tamanho da entrada, custo de comunicação, inicialização do ambiente paralelo e quantidade de trabalho executada por cada unidade de processamento.

Assim, para a contagem de palavras nos textos testados, a melhor escolha foi o processamento paralelo em CPU. A implementação em GPU permanece válida como comparação e como demonstração do uso de OpenCL com Java.

## Referências

- Oracle. Java Documentation. Disponível em: <https://docs.oracle.com/en/java/>.
- Khronos Group. OpenCL Overview. Disponível em: <https://www.khronos.org/opencl/>.
- JOCL. Java Bindings for OpenCL. Disponível em: <https://www.jocl.org/>.
- Material da disciplina de Computação Concorrente e Paralela.
- Enunciado da atividade: `analise_comparativa_algoritmos_paralelismo.pdf`.

## Anexos

Códigos principais da implementação:

| Arquivo | Descrição |
|---|---|
| [`src/Main.java`](src/Main.java) | Ponto de entrada da aplicação e comandos de execução. |
| [`src/TextNormalizer.java`](src/TextNormalizer.java) | Normalização dos textos e palavras. |
| [`src/WordCounter.java`](src/WordCounter.java) | Interface comum dos contadores. |
| [`src/SerialCPUCounter.java`](src/SerialCPUCounter.java) | Implementação serial em CPU. |
| [`src/ParallelCPUCounter.java`](src/ParallelCPUCounter.java) | Implementação paralela em CPU com threads. |
| [`src/ParallelGPUCounter.java`](src/ParallelGPUCounter.java) | Implementação paralela em GPU com JOCL. |
| [`opencl/word_count.cl`](opencl/word_count.cl) | Kernel OpenCL de contagem. |
| [`src/BenchmarkRunner.java`](src/BenchmarkRunner.java) | Execução dos benchmarks. |
| [`src/CsvWriter.java`](src/CsvWriter.java) | Geração do CSV. |
| [`src/ChartGenerator.java`](src/ChartGenerator.java) | Geração dos gráficos SVG. |

Arquivos de saída:

| Arquivo | Descrição |
|---|---|
| [`resultados/resultados.csv`](resultados/resultados.csv) | Resultados brutos dos testes. |
| [`resultados/graficos/tempo_por_metodo.svg`](resultados/graficos/tempo_por_metodo.svg) | Gráfico de tempo médio por método. |
| [`resultados/graficos/tempo_por_arquivo.svg`](resultados/graficos/tempo_por_arquivo.svg) | Gráfico de tempo médio por arquivo. |
| [`resultados/graficos/speedup_cpu.svg`](resultados/graficos/speedup_cpu.svg) | Gráfico de speedup do `ParallelCPU`. |

## Link do Projeto

Repositório no GitHub: <https://github.com/MatheuzinDev/analise_comparativa_algoritmos_paralalelismo>
