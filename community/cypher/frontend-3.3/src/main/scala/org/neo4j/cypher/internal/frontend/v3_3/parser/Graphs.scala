package org.neo4j.cypher.internal.frontend.v3_3.parser

import org.neo4j.cypher.internal.frontend.v3_3.{InputPosition, ast}
import org.neo4j.cypher.internal.frontend.v3_3.ast._
import org.parboiled.scala.{Parser, ReductionRule1, Rule1, Rule2}

trait Graphs
  extends Parser
  with Expressions {

  def GraphUrl: Rule1[ast.GraphUrl] = rule("<graph-url>") {
    ((Parameter ~~> (Left(_))) | (StringLiteral ~~> (Right(_)))) ~~>> (ast.GraphUrl(_))
  }

  def GraphRef: Rule1[ast.GraphRef] =
    Variable ~~>> (ast.GraphRef(_))

  def GraphRefList: Rule1[List[ast.GraphRef]] =
    oneOrMore(GraphRef, separator = CommaSep)

  private def GraphAlias: Rule1[ast.Variable] =
    keyword("AS") ~~ Variable

  private def GraphRefAlias: Rule1[ast.GraphRefAlias] = rule("<graph-ref> AS <name>") {
    GraphRef ~~ optional(GraphAlias) ~~>> (ast.GraphRefAlias(_, _))
  }

  private def GraphRefAliasList: Rule1[List[ast.GraphRefAlias]] =
    oneOrMore(GraphRefAlias, separator = CommaSep)

  private def GraphRefAliasItem: Rule1[ast.GraphRefAliasItem] = rule("GRAPH <graph-ref> [AS <name>]") {
    keyword("GRAPH") ~~ GraphRefAlias ~~>> (ast.GraphRefAliasItem(_))
  }

  private def GraphOfItem: Rule1[ast.GraphOfItem] = rule("GRAPH OF <pattern> [AS <name>]") {
    keyword("GRAPH") ~~ keyword("OF") ~~ Pattern ~~ optional(GraphAlias) ~~>> (ast.GraphOfItem(_, _))
  }

  private def GraphAtItem: Rule1[ast.GraphAtItem] = rule("GRAPH <graph-url> AT [AS <name>]") {
    keyword("GRAPH") ~~ keyword("AT") ~~ GraphUrl ~~ optional(GraphAlias) ~~>>(ast.GraphAtItem(_, _))
  }

  private def SourceGraphItem: Rule1[ast.SourceGraphItem] = rule("SOURCE GRAPH [AS <name>]") {
    keyword("SOURCE") ~~ keyword("GRAPH") ~~ optional(GraphAlias) ~~>> (ast.SourceGraphItem(_))
  }

  private def TargetGraphItem: Rule1[ast.TargetGraphItem] = rule("TARGET GRAPH [AS <name>]") {
    keyword("TARGET") ~~ keyword("GRAPH") ~~ optional(GraphAlias) ~~>> (ast.TargetGraphItem(_))
  }

  private def GraphOfShorthand: Rule1[ast.SingleGraphItem] =
    keyword("GRAPH") ~~ GraphAlias ~~ keyword("OF") ~~ Pattern ~~>> { (as: ast.Variable, of: ast.Pattern) => ast.GraphOfItem(of, Some(as)) }

  private def GraphAtShorthand: Rule1[ast.SingleGraphItem] =
    keyword("GRAPH") ~~ GraphAlias ~~ keyword("AT") ~~ GraphUrl ~~>> { (as: ast.Variable, url: ast.GraphUrl) => ast.GraphAtItem(url, Some(as)) }

  private def GraphAliasFirstItem = GraphOfShorthand | GraphAtShorthand

  def SingleGraphItem: Rule1[ast.SingleGraphItem] =
    SourceGraphItem | TargetGraphItem | GraphAtItem | GraphOfItem | GraphAliasFirstItem | GraphRefAliasItem

  private def SingleGraphItemList: Rule1[List[ast.SingleGraphItem]] =
    oneOrMore(SingleGraphItem, separator = CommaSep)

  private def ShortGraphItem: Rule1[ast.SingleGraphItem] =
    GraphRefAlias ~~>> (ast.GraphRefAliasItem(_))

  private def GraphItem: Rule1[ast.SingleGraphItem] =
    SingleGraphItem | ShortGraphItem

  // a >> b?
  private def ShortNewContextGraphs: Rule1[ast.NewContextGraphs] =
    GraphItem ~~ keyword(">>") ~~ optional(GraphItem) ~~>> { (source: ast.SingleGraphItem, target: Option[ast.SingleGraphItem]) => ast.NewContextGraphs(source, target)}

  // >> b
  private def ShortNewTargetGraph: Rule1[ast.NewTargetGraph] =
    keyword(">>") ~~ GraphItem ~~>> { (target: ast.SingleGraphItem) => ast.NewTargetGraph(target) }

  // a
  private def ShortReturnedGraph: Rule1[ast.ReturnedGraph] =
    ShortGraphItem ~~>> { item: ast.SingleGraphItem => ast.ReturnedGraph(item) }

  // GRAPH a >> (GRAPH b)?
  private def NewContextGraphs: Rule1[ast.NewContextGraphs] =
    SingleGraphItem ~~ keyword(">>") ~~ optional(GraphItem) ~~>> { (source: ast.SingleGraphItem, target: Option[ast.SingleGraphItem]) => ast.NewContextGraphs(source, target)}

  // >> GRAPH b
  private def NewTargetGraph: Rule1[ast.NewTargetGraph] =
    keyword(">>") ~~ SingleGraphItem ~~>> { (target: ast.SingleGraphItem) => ast.NewTargetGraph(target) }

  // GRAPH a
  private def ReturnedGraph: Rule1[ast.ReturnedGraph] =
    SingleGraphItem ~~>> { item: ast.SingleGraphItem => ast.ReturnedGraph(item) }

  private def GraphReturnItem: Rule1[ast.GraphReturnItem] =
    NewContextGraphs | NewTargetGraph | ReturnedGraph | ShortNewContextGraphs | ShortNewTargetGraph | ShortReturnedGraph

  private def ShortGraphReturnItemList: Rule1[ast.GraphReturnItems] =
    keyword("GRAPHS") ~~ oneOrMore(
      GraphReturnItem,
      separator = CommaSep
    ) ~~>> { itemsList: List[ast.GraphReturnItem] => ast.GraphReturnItems(star = false, itemsList) }

  private def ShortGraphStarReturnItemList: Rule1[ast.GraphReturnItems] =
    keyword("GRAPHS") ~~ keyword("*") ~~ optional(
      CommaSep ~~ oneOrMore(
        GraphReturnItem,
        separator = CommaSep
      ) ~~>> { itemsList: List[ast.GraphReturnItem] => ast.GraphReturnItems(star = true, itemsList) }
    ) ~~>> { foo: Option[ast.GraphReturnItems] => (pos) => foo.getOrElse(ast.GraphReturnItems(star = true, List.empty)(pos)) }

  private def GraphReturnItemList: Rule1[ast.GraphReturnItems] =
    oneOrMore(
      NewContextGraphs | NewTargetGraph | ReturnedGraph ,
      separator = CommaSep
    ) ~~>> { graphReturnItems => ast.GraphReturnItems(star = false, graphReturnItems) }

   def GraphReturnItems: Rule1[ast.GraphReturnItems] =
     ShortGraphStarReturnItemList | ShortGraphReturnItemList | GraphReturnItemList
}
