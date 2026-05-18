__kernel void count_words(
    __global const uchar *text,
    const int textLength,
    __global const uchar *word,
    const int wordLength,
    __global int *matches
) {
    int index = get_global_id(0);

    if (index >= textLength) {
        return;
    }

    matches[index] = 0;

    if (wordLength <= 0 || index + wordLength > textLength) {
        return;
    }

    if (index > 0 && text[index - 1] != ' ') {
        return;
    }

    for (int i = 0; i < wordLength; i++) {
        if (text[index + i] != word[i]) {
            return;
        }
    }

    int nextIndex = index + wordLength;
    if (nextIndex < textLength && text[nextIndex] != ' ') {
        return;
    }

    matches[index] = 1;
}
