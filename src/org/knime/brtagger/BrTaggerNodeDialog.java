package org.knime.brtagger;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentStringSelection;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * <code>NodeDialog</code> for the "BrTagger" Node.
 * Node que faz a classificação morfossintática no idioma português, utilizando as bibliotecas do Apache OpenNLP.
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more 
 * complex dialog please derive directly from 
 * {@link org.knime.core.node.NodeDialogPane}.
 * 
 * @author Daniel Andrade Costa Silva
 */
public class BrTaggerNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring BrTagger node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected BrTaggerNodeDialog() {
        super();
        
        SettingsModelString modelString = new SettingsModelString(BrTaggerNodeModel.STRSEL, null);
        addDialogComponent(new DialogComponentStringSelection(modelString, 
        		"POS Tagger:", "MAXENT", "PERCEPTRON"));
    }
}

