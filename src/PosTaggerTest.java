import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Set;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.sentdetect.*;



public class PosTaggerTest {

	public static void main(String[] args) {
		String textPath = "/home/daniel/Dropbox/Documentos/estudos/02 Business Intelligence/TCC/textos/txt/t1.txt";
		String pathToken = "/home/daniel/Dropbox/Documentos/estudos/05 knime/workspace/org.knime.brtagger/modelos/pt-token.bin";
        String pathSentence = "/home/daniel/Dropbox/Documentos/estudos/05 knime/workspace/org.knime.brtagger/modelos/pt-sent.bin";
        String pathTaggerMaxent = "/home/daniel/Dropbox/Documentos/estudos/05 knime/workspace/org.knime.brtagger/modelos/pt-pos-maxent.bin";
        String pathTaggerPerceptron = "/home/daniel/Dropbox/Documentos/estudos/05 knime/workspace/org.knime.brtagger/modelos/pt-pos-perceptron.bin";
        
        /* Carrega o texto e todos os modelos usados pelo OpenNLP */
        try {
        	InputStream documentText = new FileInputStream(textPath);
			InputStream sentenceModelFile = new FileInputStream(pathSentence);
			InputStream tokenModelFile = new FileInputStream(pathToken);
			InputStream tagModelMaxFile = new FileInputStream(pathTaggerMaxent);
			InputStream tagModelPerFile = new FileInputStream(pathTaggerPerceptron);
			
			/* Ler o arquivo e transformar em string */
			//https://stackoverflow.com/questions/326390/how-do-i-create-a-java-string-from-the-contents-of-a-file
			//https://tika.apache.org/
			byte[] encoded = Files.readAllBytes(Paths.get(textPath));
			String textContent = new String(encoded);
			
			/* Carrega o modelo de sentenças a partir do arquivo */
			SentenceModel sentenceModel = new SentenceModel(sentenceModelFile);
			/* Instancia a classe detectora de sentenças */
			SentenceDetectorME sentenceDetector = new SentenceDetectorME(sentenceModel);
			
			/* Carrega o modelo de tokens a partir do arquivo */
			TokenizerModel tokenModel = new TokenizerModel(tokenModelFile);
			/* Instancia a classe detectora de tokens */
			Tokenizer tokenizer = new TokenizerME(tokenModel);
			
			/* Idem pro Tagger */
			POSModel posModel = new POSModel(tagModelMaxFile);
			POSTaggerME tagger = new POSTaggerME(posModel);
			
			
			String sentences[] = sentenceDetector.sentDetect(textContent);
			//Span sentences2[] = sentenceDetector.sentPosDetect(textContent);
			
			HashMap<String, String> tagMap = new HashMap<String, String>();
			
			/* Descobrir os tokens de cada sentença */
			//map.merge(222L, map1, (m1, m2) -> {m1.putAll(m2);return m1;});
			
			for (String sentence : sentences) {
				//System.out.println("SENTENÇA: " + sentence);
				String tokens[] = tokenizer.tokenize(sentence);
				//Span tokenSpans[] = tokenizer.tokenizePos(sentence);
				String tags[] = tagger.tag(tokens);
				//System.out.println("(palavras, tags) = " + tokens.length + ", " + tags.length);
				for (int i=0; i < tokens.length; i++) {
					// Tá duplicando os valores pra uma mesma chave!
					//System.out.println("Token: " + tokens[i]);
					tagMap.merge(tokens[i], tags[i], (v1, v2) -> {
						return v1.contains(v2) ? v1 : v1.concat("," + v2);
					});
				}
			}
			
			/* Ver as atribuições */
			/* atribui todas as chaves para a variavel palavras */

			Set<String> palavras = tagMap.keySet();
			for (String str : palavras) {
				System.out.print(str + ": ");
				String val = tagMap.get(str);
				System.out.println(val);
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}

/*
cd "/home/daniel/Dropbox/Documentos/estudos/05 knime/workspace/org.knime.brtagger/src"

/opt/jdk1.8.0_101/bin/javac -cp /home/daniel/Programas/apache-opennlp-1.7.2/lib/opennlp-tools-1.7.2.jar PosTaggerTest.java

/opt/jdk1.8.0_101/bin/java -cp ".:/home/daniel/Programas/apache-opennlp-1.7.2/lib/opennlp-tools-1.7.2.jar" PosTaggerTest

/opt/jdk1.8.0_101/bin/java PosTaggerTest
*/