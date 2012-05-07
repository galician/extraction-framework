package org.dbpedia.extraction.mappings

import org.dbpedia.extraction.destinations.{Graph, DBpediaDatasets, Quad}
import org.dbpedia.extraction.wikiparser._
import org.dbpedia.extraction.config.mappings.TemplateParameterExtractorConfig
import org.dbpedia.extraction.ontology.{Ontology, OntologyNamespaces}
import org.dbpedia.extraction.util.Language

/**
 * Extracts template variables from template pages (see http://en.wikipedia.org/wiki/Help:Template#Handling_parameters)
 */
class TemplateParameterExtractor( context : {
                                      def ontology : Ontology
                                      def language : Language }  ) extends Extractor
{
    private val templateParameterProperty = OntologyNamespaces.getProperty("templateUsesParameter", context.language)

    val parameterRegex = """(?s)\{\{\{([^|}{<>]*)[|}<>]""".r
    
    override def extract(node : PageNode, subjectUri : String, pageContext : PageContext) : Graph =
    {
        if(node.title.namespace != Namespace.Template ||
            TemplateParameterExtractorConfig.ignoreTemplates.contains(node.title.decoded) ||
            TemplateParameterExtractorConfig.ignoreTemplatesRegex.exists(regex => regex.unapplySeq(node.title.decoded).isDefined ||
            node.isRedirect)
        ) return new Graph()

        var quads = List[Quad]()
        var parameters = List[String]()
        var linkParameters = List[String]()

        //try to get parameters inside internal links
        for (linkTemplatePar <- collectInternalLinks(node) )  {
            linkParameters ::= linkTemplatePar.toWikiText
        }

        linkParameters.distinct.foreach( link => {
            parameterRegex findAllIn link foreach (_ match {
                case parameterRegex (param) => parameters::= param //.replace("}","").replace("|","")
                case _ => parameters // FIXME: this is useless, isn't it? there's no yield and no assignment
            })
        })

        for (templatePar <- collectTemplateParameters(node) )  {
            parameters ::= templatePar.parameter
        }

        for (parameter <- parameters.distinct if parameter.nonEmpty) 
            quads ::= new Quad(context.language, DBpediaDatasets.TemplateVariables, subjectUri, templateParameterProperty, 
                parameter, node.sourceUri, context.ontology.datatypes("xsd:string"))
        new Graph(quads)
    }


    private def collectTemplateParameters(node : Node) : List[TemplateParameterNode] =
    {
        node match
        {
            case tVar : TemplateParameterNode => List(tVar)
            case _ => node.children.flatMap(collectTemplateParameters)
        }
    }

    //TODO check inside links e.g. [[Image:{{{flag}}}|125px|border]]
    private def collectInternalLinks(node : Node) : List[InternalLinkNode] =
    {
        node match
        {
            case linkNode : InternalLinkNode => List(linkNode)
            case _ => node.children.flatMap(collectInternalLinks)
        }
    }

}