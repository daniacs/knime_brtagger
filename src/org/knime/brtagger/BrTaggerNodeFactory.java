package org.knime.brtagger;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "BrTagger" Node.
 * Node que faz a classificação morfossintática no idioma português, utilizando as bibliotecas do Apache OpenNLP.
 *
 * @author Daniel Andrade Costa Silva
 */
public class BrTaggerNodeFactory 
        extends NodeFactory<BrTaggerNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public BrTaggerNodeModel createNodeModel() {
        return new BrTaggerNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<BrTaggerNodeModel> createNodeView(final int viewIndex,
            final BrTaggerNodeModel nodeModel) {
        return new BrTaggerNodeView(nodeModel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {
        return new BrTaggerNodeDialog();
    }

}

