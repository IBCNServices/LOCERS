/**
 * 
 */
package be.ugent.idlab.locers.examples;

import be.ugent.idlab.locers.cache.HashMaterializedCacheStructure;
import be.ugent.idlab.locers.cache.LOCERSMaterializeCache;
import be.ugent.idlab.locers.cache.LOCERSStructureCache;
import be.ugent.idlab.locers.cache.MaterializeCacheStructure;
import be.ugent.idlab.locers.query.CacheQuery;
import be.ugent.idlab.locers.query.CacheQueryGenerator;
import org.paukov.combinatorics3.Generator;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLEntityRenamer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class CacheSizeTest {

	static OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
	static OWLDataFactory dataFactory = manager.getOWLDataFactory();
	static final String mapping =
			"@prefix xsd: <http://www.w3.org/2001/XMLSchema#>. \n" +
					"@prefix : <http://massif/>. \n" +
					"@prefix ssn: <http://purl.oclc.org/NET/ssnx/ssn#>. \n" +
					"@prefix ct: <http://www.insight-centre.org/citytraffic#>.\n" +
					"@prefix ses: <http://www.insight-centre.org/dataset/SampleEventService#>.\n"+
					"@prefix mas: <http://massif.streaming/ontologies/rsplab/officerepository.owl#>. \n"
					+ "ct:hasValue rdf:type owl:DatatypeProperty .\n" +
					"ssn:observedProperty rdf:type owl:ObjectProperty .\n" +
					"ssn:observedBy rdf:type owl:ObjectProperty .\n" +
					":?_id a ssn:Observation ; :eventTime \"?TIMESTAMP\"^^xsd:dateTime ; ct:hasValue \"?vehicleCount\"^^xsd:int; ssn:observedProperty ses:vehicleCount ; ssn:observedBy ses:AarhusTrafficData186979 .\n"
					+ "ses:vehicleCount a ct:CongestionLevel. ";

	public static void main(String[] args) throws Exception {


		OWLOntologyManager manager = OWLManager.createConcurrentOWLOntologyManager();


		String path = "/Users/psbonte/Documents/Documents/TestWorkspace/CityBenchPlus/resource/";
		int cacheSize = 1;
		int windowSize = 100;
		int maxNum=1000;
		if(args.length >= 1) {
			path = args[0];
			cacheSize = Integer.parseInt(args[1]);
			windowSize = Integer.parseInt(args[2]);
		}
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(path + "officerepo.owl"));
		//AarhusTrafficData186979
		List<SimpleMapper> mappers = new ArrayList<SimpleMapper>(maxNum);
		Set<OWLNamedIndividual> staticInds = new HashSet<>();
		List<Integer> indexes = new ArrayList<>(maxNum);
		for(int i = 0 ; i<maxNum; i++){
			indexes.add(i);
			String trafficId="AarhusTrafficData"+i;
			String currentMapping = mapping.replaceAll("AarhusTrafficData186979",trafficId);
			SimpleMapper mapper = new SimpleMapper(currentMapping, true);
			//add the trafficID to the
			staticInds.add(dataFactory.getOWLNamedIndividual("http://www.insight-centre.org/dataset/SampleEventService#"+trafficId));
			mappers.add(mapper);
		}
		LOCERSMaterializeCache cache = new LOCERSMaterializeCache();
		cache.init(ontology);
		cache.setCacheStructure(new MaterializeCacheStructure());
		cache.addToStatic(staticInds);
		List<List<Integer>> combis = Generator.combination(indexes)
				.simple(windowSize)
				.stream()
				.limit(cacheSize)
				.collect(Collectors.toList());
		String fileName = path+"/AarhusTrafficData182955.stream";
		List<String> lines = Collections.emptyList();
		try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
			 lines = stream.collect(Collectors.toList());
		}
		List<Set<OWLAxiom>> precomputedWindows = new ArrayList<>();
		for(List<Integer> com:combis){
			System.out.println(com);
			//gererate window
			Set<OWLAxiom> windowEvent = new HashSet<>();
			for(Integer windowEl:com){
				SimpleMapper mapper =mappers.get(windowEl);
				mapper.map(lines.get(0));
				windowEvent.addAll(getAxioms(mapper.map(lines.get(1))));

			}
			//add to cache
			long time1 = System.currentTimeMillis();

			Set<OWLAxiom> result2 = cache.check(windowEvent);
			long finalTime = System.currentTimeMillis() - time1;
			System.out.println("Miss Time:\t"+finalTime);
			System.out.println("Window Size:\t"+windowEvent.size());
			System.out.println("Memory: "+MemUtils.getReallyUsedMemory());


			precomputedWindows.add(windowEvent);
		}
		//evaluate cache
		for(Set<OWLAxiom> window:precomputedWindows){
			long time1 = System.currentTimeMillis();
			Set<OWLAxiom> result2 = cache.check(window);
			long finalTime = System.currentTimeMillis() - time1;
			System.out.println("Time:\t"+finalTime);
			System.out.println(result2);
			System.out.println("Size:\t"+result2.size());
			System.out.println("Cache size:\t" + cache.getSize());
			System.out.println("Memory: "+MemUtils.getReallyUsedMemory());


		}
		System.out.println("done");


//		String fileName = path+"/AarhusTrafficData182955.stream";
//		LOREOMaterializeCache cache = new LOREOMaterializeCache();
//		cache.init(ontology);
//		cache.setCacheStructure(new MaterializeCacheStructure());
//		cache.addToStatic(staticInds);
//
//		// read file into stream, try-with-resources
//		try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
//
//			stream.map(p -> parseEvent(cache,manager,mapper.map(p),ontology)).limit(100).forEach(System.out::println);
//
//		} catch (IOException e) {
//			e.printStackTrace();
//		}





	}

	public static Set<OWLAxiom> getAxioms(String triples){
		OWLOntology eventOnt;
		try {
			OWLDataFactory fact = manager.getOWLDataFactory();
			eventOnt = manager.loadOntologyFromOntologyDocument(new StringDocumentSource(triples));
			Set<OWLAxiom> event = eventOnt.axioms().filter(ax->!ax.toString().contains("Declaration")).collect(Collectors.toSet());
			return event;
		} catch (OWLOntologyCreationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			//return Collections.emptySet();
		}
		return Collections.emptySet();
		}
	public static Set<OWLAxiom> parseEvent(LOCERSMaterializeCache cache, OWLOntologyManager manager, String triples, OWLOntology ont) {
		if(triples==null) {
			return Collections.emptySet();
		}
		// load event
		OWLOntology eventOnt;
		try {
			OWLDataFactory fact = manager.getOWLDataFactory();
			eventOnt = manager.loadOntologyFromOntologyDocument(new StringDocumentSource(triples));
			Set<OWLAxiom> event = eventOnt.axioms().filter(ax->!ax.toString().contains("Declaration")).collect(Collectors.toSet());
			Set<OWLIndividual> inds = event.stream().filter(a->a instanceof OWLClassAssertionAxiom).map(a -> ((OWLClassAssertionAxiom)a).getIndividual()).collect(Collectors.toSet());
			//add axioms
			long time1 = System.currentTimeMillis();

			Set<OWLAxiom> result2 = cache.check(event);
			//manager.saveOntology(ont, new TurtleDocumentFormat(),IRI.create(new File("test.owl").toURI()));

			//remove axioms
			manager.removeAxioms(ont, event);
			long finalTime = System.currentTimeMillis() - time1;
			System.out.println("Time:\t"+finalTime);
			System.out.println(result2);
			System.out.println("Size:\t"+result2.size());
			return result2;


		} catch (OWLOntologyCreationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			//return Collections.emptySet();
		}
		return Collections.emptySet();

	}
	private static Set<OWLAxiom> substituteStudent(Set<OWLAxiom> studentAxs) {
		final OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		OWLOntology o;
		try {
			o = m.createOntology(studentAxs);

			OWLDataFactory fact = m.getOWLDataFactory();
			final OWLEntityRenamer renamer = new OWLEntityRenamer(m, Collections.singleton(o));
			final Map<OWLEntity, IRI> entity2IRIMap = new HashMap<>();
			//find class assertion
			UUID uuid = UUID.randomUUID();

			for(OWLAxiom ax: studentAxs) {
				if(ax instanceof OWLClassAssertionAxiom) {
					OWLClassAssertionAxiom clsAx = (OWLClassAssertionAxiom)ax;
					IRI iri = clsAx.getIndividual().asOWLNamedIndividual().getIRI();
					entity2IRIMap.put(clsAx.getIndividual().asOWLNamedIndividual(),IRI.create(iri.toString()+"_"+uuid.toString()));
				}
			}
			o.applyChanges(renamer.changeIRI(entity2IRIMap));
			return o.getAxioms();
		} catch (OWLOntologyCreationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return Collections.EMPTY_SET;
	}
	public static Set<OWLAxiom> generateStudent(int id,OWLDataFactory factory){
		String iriGradStud = "http://swat.cse.lehigh.edu/onto/univ-bench.owl#UndergraduateStudent";
		String iriMemberOf = "http://swat.cse.lehigh.edu/onto/univ-bench.owl#memberOf";
		String iriDepartment = "http://www.Department2.University1.edu";
		
		OWLClass gradStudClass = factory.getOWLClass(iriGradStud);
		OWLObjectProperty memberOfProp = factory.getOWLObjectProperty(iriMemberOf);
		OWLNamedIndividual departmentInd = factory.getOWLNamedIndividual(iriDepartment);
		
		Set<OWLAxiom> event = new HashSet<OWLAxiom>();
		OWLNamedIndividual newStud = factory.getOWLNamedIndividual(iriGradStud+"_"+id);
		event.add(factory.getOWLClassAssertionAxiom(gradStudClass, newStud));
		event.add(factory.getOWLObjectPropertyAssertionAxiom(memberOfProp, newStud, departmentInd));
		
		return event;
	}
	public static Set<OWLAxiom> generateStudentMultiple(int number,int start,OWLDataFactory factory){
		String iriGradStud = "http://swat.cse.lehigh.edu/onto/univ-bench.owl#UndergraduateStudent";
		String iriMemberOf = "http://swat.cse.lehigh.edu/onto/univ-bench.owl#memberOf";
		String iriDepartment = "http://www.Department2.University1.edu";
		String hasValue = "http://example.org/lubm.owl#hasValue";
		Set<OWLAxiom> event = new HashSet<OWLAxiom>();
		OWLClass gradStudClass = factory.getOWLClass(iriGradStud);
		OWLObjectProperty memberOfProp = factory.getOWLObjectProperty(iriMemberOf);
		OWLNamedIndividual departmentInd = factory.getOWLNamedIndividual(iriDepartment);
		OWLDataProperty hasValueProp = factory.getOWLDataProperty(hasValue);
		
		for(int i = 0 ; i <number;i++){
			
		OWLNamedIndividual newStud = factory.getOWLNamedIndividual(iriGradStud+"Test_"+i+start);
		event.add(factory.getOWLClassAssertionAxiom(gradStudClass, newStud));
		event.add(factory.getOWLObjectPropertyAssertionAxiom(memberOfProp, newStud, departmentInd));
		event.add(factory.getOWLDataPropertyAssertionAxiom(hasValueProp, newStud, 1+start));

		}
		
		return event;
	}
	public static void adaptTboxForTest(int numTests, OWLOntology ontology) {
		String iri = "http://knowman.idlab.ugent.be/example#";
		for (int j = 0; j < numTests; j++) {
			manager.addAxiom(ontology,
					dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLObjectProperty(iri + "hasSupPart" + j)));
		}
	}

	public static void adaptTboxForTestExtending(int numTests, int numRelations, OWLOntology ontology) {
		String iri = "http://knowman.idlab.ugent.be/example#";
		for (int j = 0; j < numRelations; j++) {
			manager.addAxiom(ontology, dataFactory
					.getOWLDeclarationAxiom(dataFactory.getOWLObjectProperty(iri + "hasSupPart" + numTests + "_" + j)));
		}
	}

	public static Set<OWLAxiom> generateEventExtending(int i, int numRelations) {
		String iri = "http://knowman.idlab.ugent.be/example#";
		Set<OWLAxiom> event = new HashSet<OWLAxiom>();
		event.add(dataFactory.getOWLObjectPropertyAssertionAxiom(dataFactory.getOWLObjectProperty(iri + "hasSupPart"),
				dataFactory.getOWLNamedIndividual(iri + "chair2"),
				dataFactory.getOWLNamedIndividual(iri + "backSupport")));
		for (int j = 0; j < numRelations; j++) {
			event.add(dataFactory.getOWLObjectPropertyAssertionAxiom(
					dataFactory.getOWLObjectProperty(iri + "hasSupPart" + i + "_" + j),
					dataFactory.getOWLNamedIndividual(iri + "chair2"),
					dataFactory.getOWLNamedIndividual(iri + "backSupport" + i + "_" + j)));

		}
		return event;
	}

	public static Set<OWLAxiom> generateEvent(int i) {
		String iri = "http://knowman.idlab.ugent.be/example#";
		Set<OWLAxiom> event = new HashSet<OWLAxiom>();
		event.add(dataFactory.getOWLObjectPropertyAssertionAxiom(dataFactory.getOWLObjectProperty(iri + "hasSupPart"),
				dataFactory.getOWLNamedIndividual(iri + "chair2"),
				dataFactory.getOWLNamedIndividual(iri + "backSupport")));
		int j = i;
		event.add(
				dataFactory.getOWLObjectPropertyAssertionAxiom(dataFactory.getOWLObjectProperty(iri + "hasSupPart" + j),
						dataFactory.getOWLNamedIndividual(iri + "chair2"),
						dataFactory.getOWLNamedIndividual(iri + "backSupport" + j)));

		return event;
	}

	public static Set<OWLAxiom> generateEvent2() {
		Set<OWLAxiom> event = generateEvent(2);
		String iri = "http://knowman.idlab.ugent.be/example#";
		event.add(dataFactory.getOWLObjectPropertyAssertionAxiom(dataFactory.getOWLObjectProperty(iri + "hasSupPart"),
				dataFactory.getOWLNamedIndividual(iri + "chair2"), dataFactory.getOWLNamedIndividual(iri + "leg2")));
		return event;
	}

	public static void pupulateCache(LOCERSStructureCache cache, OWLOntology ontology, Set<OWLAxiom> event) {
		OWLOntology tempOnt;
		try {
			tempOnt = manager.createOntology();
			manager.addAxioms(tempOnt, event);
			Set<OWLAxiom> extended = tempOnt.individualsInSignature().map(ind -> ontology.classAssertionAxioms(ind))
					.flatMap(Function.identity()).collect(Collectors.toSet());

			extended.addAll(event);

			CacheQuery q = CacheQueryGenerator.generate(extended);
			cache.getCacheStructure().add(q, Collections.singleton(dataFactory.getOWLClass("owl:Thing")));
		} catch (OWLOntologyCreationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
