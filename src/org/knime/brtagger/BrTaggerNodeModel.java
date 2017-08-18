package org.knime.brtagger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.RowIterator;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.workflow.WorkflowEvent;
import org.knime.ext.textprocessing.data.Document;
import org.knime.ext.textprocessing.data.DocumentCell;
import org.knime.ext.textprocessing.data.DocumentValue;
import org.knime.ext.textprocessing.util.TextContainerDataCellFactory;
import org.knime.ext.textprocessing.util.TextContainerDataCellFactoryBuilder;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.sun.javafx.collections.MappingChange.Map;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;


/**
 * Implementação do modelo BrTagger.
 * Node que faz a classificação morfossintática no idioma português, utilizando as bibliotecas do Apache OpenNLP.
 *
 * @author Daniel Andrade Costa Silva
 */
public class BrTaggerNodeModel extends NodeModel {
    
    private static final NodeLogger logger = NodeLogger
            .getLogger(BrTaggerNodeModel.class);

    
    public static final String STRSEL = "tagger_model";
    private final SettingsModelString m_selStr = new SettingsModelString(STRSEL, null);
    private int indiceEscolhido = -1;
    
    /*
     * Símbolos e significados
     * http://visl.sdu.dk/visl/pt/symbolset-floresta.html
     * http://linguateca.dei.uc.pt/Floresta/BibliaFlorestal/anexo1.html
     * https://www.sketchengine.co.uk/portuguese-tagset/
     * http://corp.hum.sdu.dk/abbreviations_pt.html
     * http://visl.sdu.dk/visl/pt/info/portsymbol.html
     */
    public static final HashMap<String,String> tagDict = new HashMap<String,String>();
    static {
    	tagDict.put("adv", "Advérbio");
    	tagDict.put("adj", "Adjetivo");
    	tagDict.put("art", "Artigo");
    	tagDict.put("conj-c", "Conjunção coordenativa");
    	tagDict.put("conj-s", "Conjunção subordinativa");
    	tagDict.put("n", "Substantivo");
    	tagDict.put("n-adj", "Substantivo/Adjetivo");
    	tagDict.put("prop", "Nome próprio");
    	tagDict.put("pron-det", "Pronome determinado");
    	tagDict.put("pron-indp", "Pronome independente");
    	tagDict.put("pron-pers", "Pronome pessoal");
    	tagDict.put("v-fin", "Verbo finito"); //Indicativo, Subjuntivo, Imperativo
    	tagDict.put("v-inf", "Verbo infinito"); // infinitivo, gerúndio e particípio
    	tagDict.put("v-ger", "Verbo gerúndio");
    	tagDict.put("v-pcp", "Verbo particípio");
    	tagDict.put("prp", "Preposição");
    	tagDict.put("intj", "Interjeição");
    	tagDict.put("num", "Numeral");
    	tagDict.put("punc", "Pontuação");
    	tagDict.put("pp", "Sintagma Preposicional");
    }
    
    
    /**
     * Método construtor do modelo
     */
    protected BrTaggerNodeModel() {
        super(1, 1);
    }

    /**
     * {@inheritDoc}
     * Dividir o documento em sentenças e tokenizá-las
     * (foreach Document) - split Document in sentences
     *   foreach Sentence - tokenize Sentence
     *   foreach token - Postag(token)
     * Já será gerado um "bag of words" como saída, contendo as strings
     * e sua classificação morfossintática.
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
            final ExecutionContext exec) throws Exception {
    	
    	logger.info("------------------ ENTRANDO NO MÉTODO execute() -----------------------");
    	
    	Set<String> sd = tagDict.keySet();
    	for (String str : sd) {
    		logger.info("tagDict[" + str + "] = " + tagDict.get(str));
    	}
    	
    	//logger.info("String Selection: " + m_selStr.getStringValue());
    	/* Caminhos dos componentes da classe BrTaggerNodeModel */
        Bundle myself = FrameworkUtil.getBundle(BrTaggerNodeModel.class);
        
        /* Caminho relativo à ${workspace} */
        Path pathSentence = new Path("modelos/pt-sent.bin");
        Path pathToken = new Path("modelos/pt-token.bin");
        
        HashMap<String, Path> pathTagger = new HashMap<String, Path>();
        pathTagger.put("MAXENT", new Path("modelos/pt-pos-maxent.bin"));
        pathTagger.put("PERCEPTRON", new Path("modelos/pt-pos-perceptron.bin"));
        
        /* Sentenças */
        URL sentURL = FileLocator.find(myself, pathSentence, null);
		InputStream sentModelIn = sentURL.openStream();
		SentenceModel sentenceModel = new SentenceModel(sentModelIn);
		
		/* Tokens */
        URL tokenURL = FileLocator.find(myself, pathToken, null);
        InputStream tokenModelIn = tokenURL.openStream();
        TokenizerModel tokenModel = new TokenizerModel(tokenModelIn);
		
        /* Tagger */
        logger.info("Classificador POS: " + m_selStr.getStringValue());
        String tagIndice = m_selStr.getStringValue(); //"MAXENT" ou "PERCEPTRON"
        URL taggerURL = FileLocator.find(myself, pathTagger.get(tagIndice), null);
        InputStream taggerModelIn = taggerURL.openStream();
        POSModel posModel = new POSModel(taggerModelIn);

        /* Instancia as classes detectoras de sentenças, tokens, POS */
		SentenceDetectorME sentenceDetector = new SentenceDetectorME(sentenceModel);
		Tokenizer tokenizer = new TokenizerME(tokenModel);
		POSTaggerME tagger = new POSTaggerME(posModel);
		
		int colIndex;
		if (this.indiceEscolhido < 0) {
			logger.info("AVISO: Indice de busca não definido. Buscando campo \"Document\"");
			String colName = "Document";
			colIndex = inData[0].getDataTableSpec().findColumnIndex(colName);
			if (colIndex < 0) {
	        	throw new Exception("Não foi possível encontrar a coluna '" + colName + "'");
	        }
	        else {
	        	logger.info("Indice da coluna " + colName + ": " + colIndex);
	        }
		}
		else {
			colIndex = this.indiceEscolhido;
			logger.info("Indice definido no método configure(): " + colIndex); 
		}
		
		
		/* TODO: verificar como o processo é feito dentro do textprocessing,
		 * em que os termos são etiquetados sem gerar a Bag of Words. 
		TextContainerDataCellFactory m_termFac = TextContainerDataCellFactoryBuilder.createTermCellFactory();
		BufferedDataContainer dc;
		//TextContainerDataCellFactoryBuilder.createDocumentCellFactory();
		/* HashMap <Documento, <Token, Classificações>> */
        HashMap<String, HashMap<String, String>> docsMap = new HashMap<String, HashMap<String, String>>();
        
        /* Documento, termo, classificação */
        DataColumnSpec[] specs = new DataColumnSpec[3];
        specs[0] = new DataColumnSpecCreator("Documento", DocumentCell.TYPE).createSpec();
        specs[1] = new DataColumnSpecCreator("Termo", StringCell.TYPE).createSpec();
        specs[2] = new DataColumnSpecCreator("Classificação", StringCell.TYPE).createSpec();
        DataTableSpec dts = new DataTableSpec(specs);
        BufferedDataContainer container = exec.createDataContainer(dts);
        
        
        RowIterator it = inData[0].iterator();
        while (it.hasNext()) {
        	DataRow r = it.next();
        	DataCell c = r.getCell(colIndex);
        	DocumentValue dv = (DocumentValue)c;
        	Document d = dv.getDocument();
        	String textoDocumento = d.getText();
        	String titulo = d.getTitle();
        	
        	/* Estrutura cuja chave da hash é o titulo do documento */
        	/* Pode ser mais "eficiente" usando o indice do documento como índice do tipo int */
            HashMap<String, String> tagMap = new HashMap<String, String>();
        	//logger.info("Titulo do documento: " + titulo);
        	String sentencas[] = sentenceDetector.sentDetect(textoDocumento);
        	for (String sentenca : sentencas) {
        		String tokens[] = tokenizer.tokenize(sentenca);
        		String tags[] = tagger.tag(tokens);
                /* Estrutura que vai guardar o mapeamento token => [classe1, classe2, ...] */
        		for (int i=0; i < tokens.length; i++) {
        			String dict;
        			if (tagDict.containsKey(tags[i])) dict = tagDict.get(tags[i]);
        			else {
        				dict = tags[i];
        				logger.info("Não foi encontrado mapeamento para a tag " + dict);
        			}
        			//tagMap.merge(tokens[i], tags[i], (v1, v2) -> {
        			tagMap.merge(tokens[i], dict, (v1, v2) -> {
        				return v1.contains(v2) ? v1 : v1.concat("," + v2);
        			});
        		}
        	}
        	
        	/* Produzir a saída */
        	Set<String> allDocTokens = tagMap.keySet();
        	for (String token : allDocTokens) {
        		DataCell[] row = new DataCell[3];
        		row[0] = new DocumentCell(d);
        		row[1] = new StringCell(token);
        		row[2] = new StringCell(tagMap.get(token));
        		//row[2] = new StringCell(tagDict.get(tagMap.get(token)));
        		/* Cada linha deve ter um ID unico.... neste caso, a tupla <doc,token> */
        		container.addRowToTable(new DefaultRow(titulo+","+token, row));
        		//DocumentCell docCell = new DocumentCell(d);
            	//StringCell tokenCell = new StringCell(token);
        		//StringCell tagCell = new StringCell(tagMap.get(token));
        		//container.addRowToTable(new DefaultRow
        		//logger.info("Documento: " + titulo);
        		//logger.info("Token: " + token);
        		//logger.info("Tags: " + tagMap.get(token));
        	}
        	
        	
        	/* Depois que pegar todas as sentenças e tokens do documento,adicionar na hash */
    		docsMap.put(titulo, tagMap);
        }
        
        container.close();
        BufferedDataTable[] output = new BufferedDataTable[] { container.getTable() };
        return output;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // Models build during execute are cleared here.
        // Also data handled in load/saveInternals will be erased here.
    	logger.info("------------------ ENTRANDO NO MÉTODO reset() -----------------------");
    	logger.info("String m_selStr no reset: " + m_selStr.getKey() + " = " + m_selStr.getStringValue());
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException {
        
    	/* Verifica se existe mais de uma coluna do tipo documento */
    	/* Se tiver, escolhe apenas a primeira coluna contendo o tipo documento e ignora o resto */
    	logger.info("---------------- ENTRANDO NO MÉTODO configure()-----------------------");
    	logger.info("String m_selStr no início do configure: " + m_selStr.getKey() + " = " + m_selStr.getStringValue());
    	logger.info(inSpecs.length);
    	Iterator<DataColumnSpec> it = inSpecs[0].iterator();
    	int numOfDocs = 0;
    	int colIndice = 0;
    	String colunaEscolhida = "";
    	
    	while (it.hasNext()) {
    		DataColumnSpec spec = it.next();
    		String specName = spec.getName();
    		String specType = spec.getType().toString();
    		logger.info(spec.getName() + " -------- " + spec.getType().toString() + "::" + spec.getType().toPrettyString());
    		if (specType.equalsIgnoreCase("Text document")) {
    			numOfDocs++;
    			if (this.indiceEscolhido < 0) {
    				this.indiceEscolhido = colIndice;
    				colunaEscolhida = specName;
    			}
    		}
    		colIndice++;
    	}
    	if (numOfDocs == 1) {
    		logger.info("INFO: Apenas uma coluna do tipo Documento, na coluna " + this.indiceEscolhido + "(" + colunaEscolhida + ")");
    	}
    	else {
    		if (numOfDocs > 1) {
    			logger.info("AVISO: " + numOfDocs + " DOCUMENTOS COMO ENTRADA. ESCOLHENDO A COLUNA " + 
    				this.indiceEscolhido + "(" + colunaEscolhida + ")");
    		}
    		else {
    			/* numOfDocs == 0 */
    			logger.info("ERRO: DEVE HAVER PELO MENOS UM DOCUMENTO COMO ENTRADA!!!!!!");
    		}
    	}
    	
        DataColumnSpec[] specs = new DataColumnSpec[3];
        specs[0] = new DataColumnSpecCreator("Documento", DocumentCell.TYPE).createSpec();
        specs[1] = new DataColumnSpecCreator("Termo", StringCell.TYPE).createSpec();
        specs[2] = new DataColumnSpecCreator("Classificação", StringCell.TYPE).createSpec();
        DataTableSpec[] allSpecs = new DataTableSpec[1]; 
        DataTableSpec dts = new DataTableSpec(specs);
        allSpecs[0] = dts;
        logger.info("String m_selStr no final do configure: " + m_selStr.getKey() + " = " + m_selStr.getStringValue());
    	return allSpecs;
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        // TODO save user settings to the config object.
    	logger.info("------------------ ENTRANDO NO MÉTODO saveSettingsTo() -----------------------");
    	logger.info("String m_selStr antes de saveSettingsTo: " + m_selStr.getKey() + " = " + m_selStr.getStringValue());
    	String key = settings.getKey();
    	logger.info("settings: " + settings.getClass() + " / " + settings.toString() + " / " + key);
    	
        m_selStr.saveSettingsTo(settings);
        logger.info("String m_selStr após saveSettingsTo: " + m_selStr.getKey() + " = " + m_selStr.getStringValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
        // TODO load (valid) settings from the config object.
        // It can be safely assumed that the settings are valided by the 
        // method below.
    	logger.info("------------------ ENTRANDO NO MÉTODO loadValidatedSettingsFrom() -----------------------");
    	logger.info("String m_selStr antes de loadValidatedSettingsFrom: " + m_selStr.getKey() + " = " + m_selStr.getStringValue());
        m_selStr.loadSettingsFrom(settings);
        logger.info("String m_selStr após loadValidatedSettingsFrom: " + m_selStr.getKey() + " = " + m_selStr.getStringValue());

    }

    /**
     * Verificar se as configurações podem ser aplicadas ao modelo.
     * <b>NÃO fazer nenhuma atribuição de valores do modelo aqui!!!!!</b><BR>
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
        // TODO check if the settings could be applied to our model
        // e.g. if the count is in a certain range (which is ensured by the
        // SettingsModel).
        // Do not actually set any values of any member variables.
    	//m_str.validateSettings(settings);
    	logger.info("---------------- ENTRANDO NO MÉTODO validateSettings()-----------------------");
    	Iterator<String> it = settings.iterator();
    	while (it.hasNext()) {
    		String str = it.next();
    		logger.info("settings: " + str);
    	}
    	logger.info("String m_selStr antes de validateSettings: " + m_selStr.getKey() + " = " + m_selStr.getStringValue());
    	m_selStr.validateSettings(settings);
    	logger.info("String m_selStr após validateSettings: " + m_selStr.getKey() + " = " + m_selStr.getStringValue());
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        
        // TODO load internal data. 
        // Everything handed to output ports is loaded automatically (data
        // returned by the execute method, models loaded in loadModelContent,
        // and user settings set through loadSettingsFrom - is all taken care 
        // of). Load here only the other internals that need to be restored
        // (e.g. data used by the views).
    	logger.info("---------------- ENTRANDO NO MÉTODO loadInternals()-----------------------");
    	logger.info("String m_selStr: " + m_selStr.toString());
    	
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
       
        // TODO save internal models. 
        // Everything written to output ports is saved automatically (data
        // returned by the execute method, models saved in the saveModelContent,
        // and user settings saved through saveSettingsTo - is all taken care 
        // of). Save here only the other internals that need to be preserved
        // (e.g. data used by the views).
    	logger.info("---------------- ENTRANDO NO MÉTODO saveInternals()-----------------------");
    	logger.info("String m_selStr: " + m_selStr.toString());
    }

}