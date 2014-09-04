package com.lihaoyi.workbench

trait Api{
  def clear(): Unit
  def reload(): Unit
  def print(level: String, msg: String): Unit
  def run(path: String, bootSnippet: Option[String]): Unit
}