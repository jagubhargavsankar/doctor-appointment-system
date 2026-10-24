
package com.bhargava.doctorappointment.rag;

import jakarta.annotation.PostConstruct;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HealthcareDocumentLoader {

    private final VectorStore vectorStore;

    @Value("classpath:healthcare-guide.txt")
    private Resource healthcareGuide;

    public HealthcareDocumentLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void loadHealthcareDocument() {

        // Step 1: Read the text file.
        TextReader textReader = new TextReader(healthcareGuide);

        List<Document> documents = textReader.get();

        // Step 2: Split the document into smaller chunks.
        TextSplitter textSplitter =
                new TokenTextSplitter(200, 50, 5, 100, true);

        List<Document> chunks = textSplitter.split(documents);

        // Step 3: Store the chunks and their embeddings.
        vectorStore.add(chunks);

        System.out.println(
                "Healthcare document loaded. Chunks: " + chunks.size());
    }
}
