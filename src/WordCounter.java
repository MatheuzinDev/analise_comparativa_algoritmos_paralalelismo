public interface WordCounter {
    String name();

    long count(byte[] normalizedText, byte[] normalizedWord) throws Exception;
}
